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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class XclawInstanceService extends ServiceImpl<XclawInstanceMapper, XclawInstance> {

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

        XclawInstance instance = new XclawInstance();
        instance.setName(req.getName());
        instance.setDescription(req.getDescription());
        instance.setConfigJson(req.getConfigJson());
        instance.setUserId(userId);
        instance.setPort(port);

        // Admin or no-auth (legacy): create directly
        if (role == null || "ADMIN".equals(role)) {
            instance.setStatus("CREATING");
            save(instance);
            new Thread(() -> startOpenClawGateway(instance)).start();
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
        new Thread(() -> startOpenClawGateway(instance)).start();
    }

    private void startOpenClawGateway(XclawInstance instance) {
        try {
            String instanceId = String.valueOf(instance.getId());
            Path instanceDir = Path.of(instanceBaseDir, instanceId);
            Files.createDirectories(instanceDir);

            // Generate config JSON
            String configJson = """
            {
              "meta": {"lastTouchedVersion": "2026.4.15", "lastTouchedAt": "2026-05-04T00:00:00.000Z"},
              "gateway": {"port": %d, "bind": "loopback", "mode": "local"},
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
                  "workspace": "%s",
                  "model": {"primary": "custom/%s"},
                  "models": {"custom/%s": {}}
                }
              },
              "wizard": {"lastRunAt": "2026-02-01T11:00:00.000Z", "lastRunVersion": "2026.1.30", "lastRunCommand": "onboard", "lastRunMode": "local"}
            }
            """.formatted(
                instance.getPort(), llmUrl, llmKey,
                llmModel, llmModel,
                instanceDir.resolve("workspace").toString(),
                llmModel, llmModel
            );

            Files.writeString(instanceDir.resolve("openclaw.json"), configJson);
            Files.createDirectories(instanceDir.resolve("workspace"));

            // Start OpenClaw gateway process
            ProcessBuilder pb = new ProcessBuilder(
                "/usr/bin/node", openclawRuntime,
                "gateway",
                "--port", String.valueOf(instance.getPort()),
                "--bind", "loopback",
                "--auth", "none"
            );
            pb.environment().put("OPENCLAW_STATE_DIR", instanceDir.toString());
            pb.environment().put("OPENCLAW_CONFIG_PATH", instanceDir.resolve("openclaw.json").toString());
            pb.environment().put("OPENCLAW_SERVICE_KIND", "gateway");
            pb.environment().put("NODE_OPTIONS", "--require /usr/local/share/xclaw/csp-patch.js");
            pb.redirectError(new File("/tmp/xclaw-oc-" + instance.getId() + ".log"));
            pb.redirectOutput(new File("/tmp/xclaw-oc-" + instance.getId() + ".log"));

            Process process = pb.start();
            runningProcesses.put(instance.getId(), process);

            // Wait a bit for startup, then mark running
            Thread.sleep(3000);
            instance.setStatus("RUNNING");
            instance.setContainerId("pid:" + process.pid());
            instance.setErrorMsg(null);
            updateById(instance);

            log.info("OpenClaw instance {} started on port {}, pid={}", instance.getId(), instance.getPort(), process.pid());

            // Monitor process exit
            process.onExit().thenAccept(p -> {
                runningProcesses.remove(instance.getId());
                XclawInstance inst = getById(instance.getId());
                if (inst != null && "RUNNING".equals(inst.getStatus())) {
                    inst.setStatus("STOPPED");
                    updateById(inst);
                    log.info("OpenClaw instance {} stopped", instance.getId());
                }
            });

        } catch (Exception e) {
            log.error("Failed to start OpenClaw for instance {}", instance.getId(), e);
            instance.setStatus("ERROR");
            instance.setErrorMsg("启动失败: " + e.getMessage());
            updateById(instance);
        }
    }

    public void startInstance(Long id) {
        XclawInstance instance = getById(id);
        if (instance == null) throw new RuntimeException("Instance not found");
        if (!runningProcesses.containsKey(id) || !runningProcesses.get(id).isAlive()) {
            startOpenClawGateway(instance);
        }
    }

    public void stopInstance(Long id) {
        XclawInstance instance = getById(id);
        if (instance == null) throw new RuntimeException("Instance not found");
        Process p = runningProcesses.remove(id);
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
        }
        instance.setStatus("STOPPED");
        updateById(instance);
    }

    public void deleteInstance(Long id) {
        stopInstance(id);
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
        Process p = runningProcesses.get(id);
        if (p != null && p.isAlive()) {
            instance.setStatus("RUNNING");
        } else if ("RUNNING".equals(instance.getStatus())) {
            instance.setStatus("STOPPED");
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
}
