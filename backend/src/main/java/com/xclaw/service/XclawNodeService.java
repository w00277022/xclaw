package com.xclaw.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xclaw.entity.XclawNode;
import com.xclaw.mapper.XclawNodeMapper;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class XclawNodeService extends ServiceImpl<XclawNodeMapper, XclawNode> {

    /**
     * Ensure a default local node exists.
     */
    public void ensureLocalNode() {
        LambdaQueryWrapper<XclawNode> wrapper = new LambdaQueryWrapper<XclawNode>()
                .eq(XclawNode::getIsLocal, true);
        if (count(wrapper) == 0) {
            XclawNode node = new XclawNode();
            node.setName("本机节点");
            node.setHost("localhost");
            node.setPort(22);
            node.setStatus("ONLINE");
            node.setIsLocal(true);
            save(node);
            log.info("Created default local node");
        }
    }

    /**
     * Test SSH connectivity to a remote node.
     */
    public boolean testConnection(XclawNode node) {
        if (node.getIsLocal() != null && node.getIsLocal()) return true;
        try {
            Session session = createSession(node);
            session.connect(5000);
            session.disconnect();
            return true;
        } catch (Exception e) {
            log.warn("SSH connection test failed for node {}: {}", node.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Execute a command on a remote node via SSH.
     * Returns the command output (stdout).
     */
    public String executeRemoteCommand(XclawNode node, String command, int timeoutSeconds) throws Exception {
        if (node.getIsLocal() != null && node.getIsLocal()) {
            // Execute locally
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            return output;
        }

        Session session = createSession(node);
        session.connect(5000);
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        channel.setErrStream(null);
        InputStream in = channel.getInputStream();
        channel.connect();

        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[4096];
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (true) {
            while (in.available() > 0) {
                int len = in.read(buf, 0, buf.length);
                if (len < 0) break;
                sb.append(new String(buf, 0, len));
            }
            if (channel.isClosed()) break;
            if (System.currentTimeMillis() > deadline) {
                channel.disconnect();
                throw new RuntimeException("Command timed out after " + timeoutSeconds + "s");
            }
            Thread.sleep(100);
        }
        channel.disconnect();
        session.disconnect();
        return sb.toString();
    }

    private Session createSession(XclawNode node) throws JSchException {
        JSch jsch = new JSch();
        if (node.getSshKey() != null && !node.getSshKey().isEmpty()) {
            jsch.addIdentity("node-" + node.getId(), node.getSshKey().getBytes(), null, null);
        }
        String user = node.getSshUser() != null ? node.getSshUser() : "root";
        Session session = jsch.getSession(user, node.getHost(), node.getPort() != null ? node.getPort() : 22);
        if (node.getSshKey() == null || node.getSshKey().isEmpty()) {
            // If no key, try password auth (should be configured via host key checking)
            session.setConfig("StrictHostKeyChecking", "no");
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(5000);
        return session;
    }

    public List<XclawNode> listOnlineNodes() {
        return list(new LambdaQueryWrapper<XclawNode>().eq(XclawNode::getStatus, "ONLINE"));
    }
}
