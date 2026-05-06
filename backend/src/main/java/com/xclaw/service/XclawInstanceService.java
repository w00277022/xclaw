package com.xclaw.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xclaw.dto.CreateXclawRequest;
import com.xclaw.entity.Approval;
import com.xclaw.entity.XclawInstance;
import com.xclaw.mapper.XclawInstanceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class XclawInstanceService extends ServiceImpl<XclawInstanceMapper, XclawInstance> {

    @Value("${xclaw.host:localhost}")
    private String xclawHost;

    @Value("${xclaw.remote-access:false}")
    private boolean remoteAccess;

    @Value("${openclaw.backend.url}")
    private String llmUrl;

    @Value("${openclaw.backend.api-key}")
    private String llmKey;

    @Value("${openclaw.backend.model}")
    private String llmModel;

    @Value("${openclaw.runtime:/usr/lib/node_modules/openclaw/dist/index.js}")
    private String openclawRuntime;

    @Value("${openclaw.instance-dir:/root/.openclaw-instances}")
    private String instanceBaseDir;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private com.xclaw.service.UserService userService;

    @Autowired
    private com.xclaw.service.XclawNodeService nodeService;

    // Track running processes: instanceId -> Process
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    /**
     * Create instance with user context for approval.
     * @param userId null if no auth (legacy) or admin
     * @param role null if no auth (legacy), "ADMIN" to skip approval
     * @param requesterName username for approval record
     */
    public XclawInstance createInstance(CreateXclawRequest req, Long userId, String role, String requesterName) {
        int port = findAvailablePort();
        String type = req.getType();
        if (type == null || type.isEmpty()) type = "openclaw";

        // Check user permissions for instance type
        if (userId != null && !"ADMIN".equals(role)) {
            com.xclaw.entity.User user = userService.getById(userId);
            if (user != null) {
                if ("hermes".equals(type) && (user.getCanCreateHermes() == null || !user.getCanCreateHermes())) {
                    throw new RuntimeException("您没有创建 Hermes-Agent 实例的权限，请联系管理员");
                }
                if ("openclaw".equals(type) && (user.getCanCreateOpenclaw() == null || !user.getCanCreateOpenclaw())) {
                    throw new RuntimeException("您没有创建 OpenClaw 实例的权限，请联系管理员");
                }
            }
        }

        XclawInstance instance = new XclawInstance();
        instance.setName(req.getName());
        instance.setDescription(req.getDescription());
        instance.setConfigJson(req.getConfigJson());
        instance.setUserId(userId);
        instance.setPort(port);
        instance.setType(type);
        instance.setNodeId(req.getNodeId());

        // Admin or no-auth (legacy): create directly
        if (role == null || "ADMIN".equals(role)) {
            instance.setStatus("CREATING");
            save(instance);
            if ("hermes".equals(type)) {
                new Thread(() -> startHermesContainer(instance)).start();
            } else {
                new Thread(() -> startOpenClawGateway(instance)).start();
            }
        } else {
            // Regular user: pending approval
            instance.setStatus("PENDING_APPROVAL");
            save(instance);

            Approval approval = new Approval();
            approval.setInstanceId(instance.getId());
            approval.setUserId(userId);
            approval.setInstanceName(instance.getName());
            approval.setInstanceDescription(instance.getDescription());
            approval.setRequesterName(requesterName);
            approval.setStatus("PENDING");
            approvalService.save(approval);
        }

        return instance;
    }

    /** Admin approves and starts the instance */
    public void approveAndStartInstance(Long instanceId) {
        XclawInstance instance = getById(instanceId);
        if (instance == null) return;
        instance.setStatus("CREATING");
        instance.setErrorMsg(null);
        updateById(instance);
        if ("hermes".equals(instance.getType())) {
            new Thread(() -> startHermesContainer(instance)).start();
        } else {
            new Thread(() -> startOpenClawGateway(instance)).start();
        }
    }

    private void startOpenClawGateway(XclawInstance instance) {
        try {
            com.xclaw.entity.XclawNode node = resolveNode(instance.getNodeId());
            boolean isRemote = node != null && (node.getIsLocal() == null || !node.getIsLocal());

            String instanceId = String.valueOf(instance.getId());
            String instanceDir = (isRemote ? "/opt/xclaw-instances" : instanceBaseDir) + "/" + instanceId;

            if (!isRemote) {
                Files.createDirectories(Path.of(instanceDir));
            }

            // Determine bind address
            String bindAddr = remoteAccess ? "lan" : "loopback";
            String gatewayToken = null;
            if (remoteAccess) {
                gatewayToken = java.util.UUID.randomUUID().toString().replace("-", "");
                instance.setGatewayToken(gatewayToken);
            } else {
                instance.setGatewayToken(null);
            }
            log.info("Starting OpenClaw instance {} with bind={} (remoteAccess={}) on node={}", instanceId, bindAddr, remoteAccess, node != null ? node.getName() : "local");

            // Generate config JSON
            String gatewayAuthBlock = remoteAccess ? ", \"auth\": {\"token\": \"%s\"}".formatted(gatewayToken) : "";
            String controlUiBlock = remoteAccess ? ", \"controlUi\": {\"allowedOrigins\": [\"*\"], \"dangerouslyAllowHostHeaderOriginFallback\": true, \"dangerouslyDisableDeviceAuth\": true}" : "";
            String configJson = """
            {
              "meta": {"lastTouchedVersion": "2026.4.15", "lastTouchedAt": "2026-05-04T00:00:00.000Z"},
              "gateway": {"port": %d, "bind": "%s", "mode": "local"%s%s},
              "models": {
                "providers": {
                  "custom": {
                    "baseUrl": "%s",
                    "apiKey": "%s",
                    "api": "openai-completions",
                    "models": [{
                      "id": "%s", "name": "%s", "reasoning": false,
                      "input": ["text"], "contextWindow": 200000, "maxTokens": 8192,
                      "compat": {"supportsDeveloperRole": false, "supportsUsageInStreaming": true}
                    }]
                  }
                },
                "mode": "merge"
              },
              "agents": {
                "defaults": {
                  "workspace": "%s/workspace",
                  "model": {"primary": "custom/%s"},
                  "models": {"custom/%s": {}}
                }
              },
              "wizard": {"lastRunAt": "2026-02-01T11:00:00.000Z", "lastRunVersion": "2026.1.30", "lastRunCommand": "onboard", "lastRunMode": "local"}
            }
            """.formatted(
                instance.getPort(), bindAddr, controlUiBlock, gatewayAuthBlock,
                llmUrl, llmKey,
                llmModel, llmModel,
                instanceDir,
                llmModel, llmModel
            );

            if (isRemote) {
                // Remote node: SSH to execute commands
                String sshCmd = String.format(
                    "mkdir -p %s/workspace && cat > %s/openclaw.json << 'XCLAW_EOF'\n%s\nXCLAW_EOF\n" +
                    "nohup /usr/bin/node %s gateway --port %d --bind %s %s > /tmp/xclaw-oc-%s.log 2>&1 & echo $!",
                    instanceDir, instanceDir, configJson,
                    openclawRuntime, instance.getPort(), bindAddr,
                    gatewayToken != null ? "--token " + gatewayToken : "--auth none",
                    instanceId
                );
                String output = nodeService.executeRemoteCommand(node, sshCmd, 30).trim();
                String pid = output.split("\n")[output.split("\n").length - 1].trim();
                instance.setStatus("RUNNING");
                instance.setContainerId("remote-pid:" + pid + "@" + node.getHost());
                instance.setErrorMsg(null);
                updateById(instance);
                log.info("OpenClaw instance {} started on remote node {} ({}:{}) pid={}", instanceId, node.getName(), node.getHost(), instance.getPort(), pid);
            } else {
                // Local execution
                Files.writeString(Path.of(instanceDir).resolve("openclaw.json"), configJson);
                Files.createDirectories(Path.of(instanceDir).resolve("workspace"));

                java.util.ArrayList<String> cmd = new java.util.ArrayList<>(java.util.List.of(
                    "/usr/bin/node", openclawRuntime,
                    "gateway",
                    "--port", String.valueOf(instance.getPort()),
                    "--bind", bindAddr
                ));
                if (gatewayToken != null) {
                    cmd.add("--token"); cmd.add(gatewayToken);
                } else {
                    cmd.add("--auth"); cmd.add("none");
                }
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.environment().put("OPENCLAW_STATE_DIR", instanceDir);
                pb.environment().put("OPENCLAW_CONFIG_PATH", Path.of(instanceDir).resolve("openclaw.json").toString());
                pb.environment().put("OPENCLAW_SERVICE_KIND", "gateway");
                pb.environment().put("NODE_OPTIONS", "--require /usr/local/share/xclaw/csp-patch.js");
                pb.redirectError(new File("/tmp/xclaw-oc-" + instance.getId() + ".log"));
                pb.redirectOutput(new File("/tmp/xclaw-oc-" + instance.getId() + ".log"));

                Process process = pb.start();
                runningProcesses.put(instance.getId(), process);

                Thread.sleep(3000);
                instance.setStatus("RUNNING");
                instance.setContainerId("pid:" + process.pid());
                instance.setErrorMsg(null);
                updateById(instance);

                log.info("OpenClaw instance {} started on port {}, pid={}", instance.getId(), instance.getPort(), process.pid());

                process.onExit().thenAccept(p -> {
                    runningProcesses.remove(instance.getId());
                    XclawInstance inst = getById(instance.getId());
                    if (inst != null && "RUNNING".equals(inst.getStatus())) {
                        inst.setStatus("STOPPED");
                        updateById(inst);
                        log.info("OpenClaw instance {} stopped", instance.getId());
                    }
                });
            }

        } catch (Exception e) {
            log.error("Failed to start OpenClaw for instance {}", instance.getId(), e);
            instance.setStatus("ERROR");
            instance.setErrorMsg("启动失败: " + e.getMessage());
            updateById(instance);
        }
    }

    /** Resolve node by ID; returns null for local-only (nodeId=null). */
    private com.xclaw.entity.XclawNode resolveNode(Long nodeId) {
        if (nodeId == null) return null;
        return nodeService.getById(nodeId);
    }

    /**
     * Kill a tracked OpenClaw process by instance id (in-memory map).
     */
    private void killOpenClawProcess(Long id) {
        Process p = runningProcesses.remove(id);
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            log.info("Killed lingering OpenClaw process for instance {}", id);
        }
    }

    /**
     * Kill any process listening on the given port.
     * Uses lsof to find the PID, then kill -9.
     */
    private void killByPort(Integer port) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c",
                "lsof -ti:" + port + " 2>/dev/null | xargs -r kill -9");
            pb.start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            log.info("Killed orphaned process on port {}", port);
        } catch (Exception e) {
            log.debug("No process to kill on port {}: {}", port, e.getMessage());
        }
    }

    /**
     * Kill a process by stored PID string (format: "pid:12345").
     */
    private void killByPid(String containerId) {
        try {
            long pid = Long.parseLong(containerId.substring(4));
            ProcessHandle.of(pid).ifPresent(ph -> {
                if (ph.isAlive()) {
                    ph.destroyForcibly();
                    log.info("Killed lingering process by PID {}", pid);
                }
            });
        } catch (NumberFormatException e) {
            log.warn("Invalid pid in containerId: {}", containerId);
        }
    }

    /**
     * Check whether a process with the given PID exists and is alive.
     */
    private boolean isPidAlive(String containerId) {
        if (containerId == null || !containerId.startsWith("pid:")) return false;
        try {
            long pid = Long.parseLong(containerId.substring(4));
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void startInstance(Long id) {
        XclawInstance instance = getById(id);
        if (instance == null) throw new RuntimeException("Instance not found");
        if ("hermes".equals(instance.getType())) {
            startHermesContainer(instance);
        } else {
            com.xclaw.entity.XclawNode node = resolveNode(instance.getNodeId());
            boolean isRemote = node != null && (node.getIsLocal() == null || !node.getIsLocal());
            if (isRemote) {
                // For remote, just re-run startOpenClawGateway which handles SSH
                startOpenClawGateway(instance);
            } else {
                killOpenClawProcess(id);
                if (instance.getContainerId() != null && instance.getContainerId().startsWith("pid:")) {
                    killByPid(instance.getContainerId());
                }
                if (instance.getPort() != null && instance.getPort() > 0) {
                    killByPort(instance.getPort());
                }
                startOpenClawGateway(instance);
            }
        }
    }

    public void stopInstance(Long id) {
        XclawInstance instance = getById(id);
        if (instance == null) throw new RuntimeException("Instance not found");
        if ("hermes".equals(instance.getType())) {
            stopHermesContainer(instance);
        } else {
            com.xclaw.entity.XclawNode node = resolveNode(instance.getNodeId());
            boolean isRemote = node != null && (node.getIsLocal() == null || !node.getIsLocal());
            if (isRemote) {
                // Stop via SSH
                String containerId = instance.getContainerId();
                if (containerId != null && containerId.startsWith("remote-pid:")) {
                    String pid = containerId.split("@")[0].substring("remote-pid:".length());
                    try {
                        nodeService.executeRemoteCommand(node, "kill " + pid + " 2>/dev/null || true", 10);
                    } catch (Exception e) {
                        log.warn("Failed to stop remote instance {}: {}", id, e.getMessage());
                    }
                }
            } else {
                Process p = runningProcesses.remove(id);
                if (p != null && p.isAlive()) p.destroyForcibly();
                if (instance.getContainerId() != null && instance.getContainerId().startsWith("pid:")) {
                    killByPid(instance.getContainerId());
                }
                if (instance.getPort() != null && instance.getPort() > 0) {
                    killByPort(instance.getPort());
                }
            }
        }
        instance.setStatus("STOPPED");
        updateById(instance);
    }

    public void deleteInstance(Long id) {
        XclawInstance inst = getById(id);
        if (inst == null) throw new RuntimeException("Instance not found");
        stopInstance(id);
        if ("hermes".equals(inst.getType())) {
            removeHermesContainer(id);
        }
        removeById(id);
        // Clean up instance directory
        try {
            Path dir = Path.of(instanceBaseDir, String.valueOf(id));
            if (Files.exists(dir)) {
                Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } catch (Exception e) {
            log.warn("Failed to clean instance dir for {}", id, e);
        }
    }

    public void syncStatus(Long id) {
        XclawInstance instance = getById(id);
        if (instance == null) return;
        if ("hermes".equals(instance.getType())) {
            syncHermesStatus(instance);
        } else {
            com.xclaw.entity.XclawNode node = resolveNode(instance.getNodeId());
            boolean isRemote = node != null && (node.getIsLocal() == null || !node.getIsLocal());
            if (isRemote) {
                // Check remote process via SSH
                String containerId = instance.getContainerId();
                if (containerId != null && containerId.startsWith("remote-pid:")) {
                    String pid = containerId.split("@")[0].substring("remote-pid:".length());
                    try {
                        String result = nodeService.executeRemoteCommand(node, "kill -0 " + pid + " 2>/dev/null && echo ALIVE || echo DEAD", 5);
                        if (result.trim().contains("ALIVE")) {
                            instance.setStatus("RUNNING");
                        } else if ("RUNNING".equals(instance.getStatus())) {
                            instance.setStatus("STOPPED");
                        }
                    } catch (Exception e) {
                        if ("RUNNING".equals(instance.getStatus())) instance.setStatus("OFFLINE");
                    }
                }
            } else {
                Process p = runningProcesses.get(id);
                if (p != null && p.isAlive()) {
                    instance.setStatus("RUNNING");
                } else if ("RUNNING".equals(instance.getStatus())) {
                    if (isPidAlive(instance.getContainerId())) {
                        log.info("Instance {} process still alive (checked by stored PID), keeping RUNNING", id);
                    } else {
                        instance.setStatus("STOPPED");
                    }
                }
            }
        }
        updateById(instance);
    }

    public List<XclawInstance> listAll(Long userId, String role) {
        LambdaQueryWrapper<XclawInstance> wrapper = new LambdaQueryWrapper<XclawInstance>()
                .orderByDesc(XclawInstance::getCreatedAt);
        // Non-admin users only see their own instances
        if (userId != null && !"ADMIN".equals(role)) {
            wrapper.eq(XclawInstance::getUserId, userId);
        }
        return list(wrapper);
    }

    private int findAvailablePort() {
        List<XclawInstance> all = list();
        int port = 9200;
        for (XclawInstance inst : all) {
            if (inst.getPort() != null && inst.getPort() >= port) {
                port = inst.getPort() + 1;
            }
        }
        return Math.max(9200, port);
    }

    // ======================== Hermes Docker Container Management ========================

    @Value("${hermes.docker.image:hermes-agent:latest}")
    private String hermesDockerImage;

    /** Hermes container internal port — the HTTP bridge listens on this port inside the container */
    private static final int HERMES_INTERNAL_PORT = 3100;

    private void startHermesContainer(XclawInstance instance) {
        try {
            String instanceId = String.valueOf(instance.getId());
            String containerName = "xclaw-hermes-" + instanceId;

            // Check if docker is available
            ProcessBuilder checkPb = new ProcessBuilder("docker", "info");
            checkPb.redirectError(new File("/dev/null"));
            checkPb.redirectOutput(new File("/dev/null"));
            Process checkProc = checkPb.start();
            if (checkProc.waitFor() != 0) {
                throw new RuntimeException("Docker daemon 不可用");
            }

            // Remove existing container if any
            ProcessBuilder rmPb = new ProcessBuilder("docker", "rm", "-f", containerName);
            rmPb.redirectError(new File("/dev/null"));
            rmPb.redirectOutput(new File("/dev/null"));
            rmPb.start().waitFor();

            // Create instance directory for workspace persistence
            Path instanceDir = Path.of(instanceBaseDir, instanceId);
            Files.createDirectories(instanceDir.resolve("workspace"));

            // Prepare hermes home directory with config
            Path hermesHome = instanceDir.resolve("hermes-home");
            Files.createDirectories(hermesHome);

            // Write .env for Hermes LLM configuration
            // Hermes uses provider-specific env vars; we configure it for custom OpenAI-compatible endpoint
            String envContent = String.format("""
                OPENAI_API_KEY=%s
                OPENAI_BASE_URL=%s/v1
                """, llmKey, llmUrl);
            Files.writeString(hermesHome.resolve(".env"), envContent);

            // Write config.yaml with model and provider settings
            String configYaml = String.format("""
                model:
                  default: "%s"
                  provider: "custom"
                  base_url: "%s/v1"
                terminal:
                  backend: local
                  timeout: 120
                """, llmModel, llmUrl);
            Files.writeString(hermesHome.resolve("config.yaml"), configYaml);

            // Copy HTTP bridge script into container
            Path bridgeScript = Path.of(System.getProperty("user.home"), ".openclaw/workspace/xclaw-platform/docker/hermes-http-bridge.py");

            // Run Hermes container with:
            // - Port mapping: hostPort -> 3100 (container internal)
            // - HTTP bridge as entrypoint (overrides default ACP+gateway)
            // - Mount hermes home for config persistence
            // - Mount workspace for file persistence
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "-d",
                "--name", containerName,
                "-p", instance.getPort() + ":" + HERMES_INTERNAL_PORT,
                "-v", instanceDir.resolve("workspace").toString() + ":/opt/data/workspace",
                "-v", hermesHome.toString() + ":/opt/data",
                "-v", bridgeScript.toString() + ":/opt/hermes/hermes-http-bridge.py:ro",
                "-e", "HERMES_HTTP_PORT=" + HERMES_INTERNAL_PORT,
                "-e", "ACP_TCP_PORT=" + HERMES_INTERNAL_PORT,
                "-e", "OPENAI_API_KEY=" + llmKey,
                "-e", "OPENAI_BASE_URL=" + llmUrl + "/v1",
                "-e", "HERMES_MODEL=" + llmModel,
                "--restart", "unless-stopped",
                "--entrypoint", "bash",
                hermesDockerImage,
                "-c", "source /opt/hermes/.venv/bin/activate && python3 /opt/hermes/hermes-http-bridge.py"
            );
            pb.redirectError(new File("/tmp/xclaw-hermes-" + instance.getId() + ".log"));
            pb.redirectOutput(new File("/tmp/xclaw-hermes-" + instance.getId() + ".log"));

            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Read docker logs for error details
                String logs = readDockerLogs(containerName);
                throw new RuntimeException("docker run failed with exit code " + exitCode + ": " + logs);
            }

            // Get container ID
            ProcessBuilder inspectPb = new ProcessBuilder("docker", "inspect", "--format", "{{.Id}}", containerName);
            inspectPb.redirectError(new File("/dev/null"));
            Process inspectProc = inspectPb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inspectProc.getInputStream()));
            String containerId = reader.readLine();
            inspectProc.waitFor();

            // Wait for HTTP bridge to become ready
            boolean ready = waitForHermesReady(instance.getPort(), 15);
            if (!ready) {
                String logs = readDockerLogs(containerName);
                throw new RuntimeException("Hermes HTTP bridge 未就绪: " + logs);
            }

            instance.setStatus("RUNNING");
            instance.setContainerId(containerId != null ? containerId.trim() : containerName);
            instance.setErrorMsg(null);
            updateById(instance);

            log.info("Hermes instance {} started as Docker container {} on port {}", instance.getId(), containerName, instance.getPort());

        } catch (Exception e) {
            log.error("Failed to start Hermes container for instance {}", instance.getId(), e);
            instance.setStatus("ERROR");
            instance.setErrorMsg("Hermes启动失败: " + e.getMessage());
            updateById(instance);
        }
    }

    /** Wait for Hermes HTTP bridge to respond to /health */
    private boolean waitForHermesReady(int port, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("http://" + xclawHost + ":" + port + "/health").openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                if (conn.getResponseCode() == 200) return true;
            } catch (Exception ignored) {}
            try { Thread.sleep(1000); } catch (InterruptedException e) { return false; }
        }
        return false;
    }

    /** Read recent docker container logs */
    private String readDockerLogs(String containerName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "logs", "--tail", "20", containerName);
            pb.redirectError(new File("/dev/null"));
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            proc.waitFor();
            return sb.toString().trim();
        } catch (Exception e) {
            return "(无法读取日志: " + e.getMessage() + ")";
        }
    }

    private void stopHermesContainer(XclawInstance instance) {
        try {
            String containerName = "xclaw-hermes-" + instance.getId();
            ProcessBuilder pb = new ProcessBuilder("docker", "stop", containerName);
            pb.redirectError(new File("/dev/null"));
            pb.redirectOutput(new File("/dev/null"));
            pb.start().waitFor();
            log.info("Hermes container {} stopped", containerName);
        } catch (Exception e) {
            log.warn("Failed to stop Hermes container for instance {}", instance.getId(), e);
        }
    }

    private void removeHermesContainer(Long id) {
        try {
            String containerName = "xclaw-hermes-" + id;
            ProcessBuilder pb = new ProcessBuilder("docker", "rm", "-f", containerName);
            pb.redirectError(new File("/dev/null"));
            pb.redirectOutput(new File("/dev/null"));
            pb.start().waitFor();
            log.info("Hermes container {} removed", containerName);
        } catch (Exception e) {
            log.warn("Failed to remove Hermes container for instance {}", id, e);
        }
    }

    private void syncHermesStatus(XclawInstance instance) {
        try {
            String containerName = "xclaw-hermes-" + instance.getId();
            ProcessBuilder pb = new ProcessBuilder("docker", "inspect", "--format", "{{.State.Running}}", containerName);
            pb.redirectError(new File("/dev/null"));
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String running = reader.readLine();
            proc.waitFor();
            if ("true".equals(running != null ? running.trim() : "")) {
                instance.setStatus("RUNNING");
            } else {
                if ("RUNNING".equals(instance.getStatus())) {
                    instance.setStatus("STOPPED");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to sync Hermes status for instance {}", instance.getId(), e);
        }
    }
}
