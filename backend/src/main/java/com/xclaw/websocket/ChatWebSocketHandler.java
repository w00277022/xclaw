package com.xclaw.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xclaw.entity.ChatMessage;
import com.xclaw.entity.XclawInstance;
import com.xclaw.service.ChatMessageService;
import com.xclaw.service.XclawInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final XclawInstanceService instanceService;
    private final ChatMessageService chatMessageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final byte[] ED25519_SPKI_PREFIX = hexToBytes("302a300506032b6570032100");

    @Value("${xclaw.host:localhost}")
    private String xclawHost;

    public ChatWebSocketHandler(XclawInstanceService instanceService, ChatMessageService chatMessageService) {
        this.instanceService = instanceService;
        this.chatMessageService = chatMessageService;
    }

    private final Map<String, Long> sessionInstanceMap = new ConcurrentHashMap<>();
    private final Map<String, java.net.http.WebSocket> proxySocketMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long instanceId = extractInstanceId(session);
        if (instanceId == null) {
            try { session.close(CloseStatus.BAD_DATA); } catch (IOException e) {}
            return;
        }
        sessionInstanceMap.put(session.getId(), instanceId);
        log.info("Chat WS connected for instance {}", instanceId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long instanceId = sessionInstanceMap.get(session.getId());
        if (instanceId == null) return;

        XclawInstance instance = instanceService.getById(instanceId);
        if (instance == null || instance.getPort() == null || instance.getPort() < 9200) {
            sendError(session, "实例未运行");
            return;
        }

        JsonNode payload = objectMapper.readTree(message.getPayload());

        // New session request
        if (payload.has("newSession") && payload.get("newSession").asBoolean()) {
            String label = payload.has("label") ? payload.get("label").asText() : "新会话";
            log.info("Creating new session for instance {}: {}", instanceId, label);
            createNewSession(session, instance, instanceId, label);
            return;
        }

        // Regular chat message
        String userMessage = payload.has("content") ? payload.get("content").asText() : "";
        String sessionKey = payload.has("sessionKey") ? payload.get("sessionKey").asText() : "agent:main:main";
        if (userMessage.isEmpty()) return;

        // Extract attachment info
        String attachmentInfo = "";
        if (payload.has("attachment")) {
            JsonNode att = payload.get("attachment");
            String fileName = att.has("fileName") ? att.get("fileName").asText() : "unknown";
            String fileKey = att.has("fileKey") ? att.get("fileKey").asText() : "";
            attachmentInfo = "\n[附件: " + fileName;
            if (!fileKey.isEmpty()) {
                try {
                    String content = Files.readString(Path.of(System.getProperty("java.io.tmpdir"), "xclaw-uploads", fileKey));
                    if (!content.isEmpty()) attachmentInfo += "\n文件内容:\n" + content.substring(0, Math.min(content.length(), 8000));
                } catch (IOException ignored) {}
            }
            attachmentInfo += "]";
        }

        chatMessageService.saveMessage(instanceId, sessionKey, "user", userMessage);
        log.info("Chat [{}] session={}: {}", instanceId, sessionKey, userMessage.substring(0, Math.min(50, userMessage.length())));

        // Hermes type: use HTTP API
        if ("hermes".equals(instance.getType())) {
            String combinedMessage = userMessage + attachmentInfo;
            handleHermesChat(session, instance, combinedMessage, sessionKey, instanceId);
            return;
        }

        String combinedMessage = userMessage + attachmentInfo;
        connectAndChat(session, instance, combinedMessage, sessionKey, instanceId);
    }

    /**
     * Create a new session by generating a unique sessionKey.
     * No need to spawn on OpenClaw — chat.send with a new sessionKey auto-creates the context.
     */
    private void createNewSession(WebSocketSession session, XclawInstance instance, Long instanceId, String label) {
        String sessionKey = "agent:main:chat-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("New session created: {} for instance {}", sessionKey, instanceId);
        try {
            ObjectNode resp = objectMapper.createObjectNode();
            resp.put("sessionCreated", true);
            resp.put("sessionKey", sessionKey);
            resp.put("label", label);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
        } catch (IOException e) {
            log.error("Failed to send sessionCreated", e);
        }
    }

    /**
     * Handle chat for Hermes-agent instances via HTTP API.
     * Hermes container runs an HTTP bridge on port 3100 (mapped to instance port).
     * POST /api/chat accepts {"task": "...", "sessionKey": "..."} and returns {"result": "..."}.
     */
    private void handleHermesChat(WebSocketSession session, XclawInstance instance,
            String userMessage, String sessionKey, Long instanceId) {
        CompletableFuture.runAsync(() -> {
            try {
                int port = instance.getPort();
                String hermesUrl = "http://" + xclawHost + ":" + port + "/api/chat";

                ObjectNode reqBody = objectMapper.createObjectNode();
                reqBody.put("task", userMessage);
                reqBody.put("sessionKey", sessionKey);

                HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
                java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(hermesUrl))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofMinutes(5))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(reqBody)))
                    .build();

                // Send initial streaming indicator
                sendChunk(session, "", false);

                java.net.http.HttpResponse<String> response = httpClient.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode result = objectMapper.readTree(response.body());
                    String content = result.path("result").asText(result.path("output").asText(response.body()));
                    sendChunk(session, content, false);
                    sendChunk(session, "", true);
                    chatMessageService.saveMessage(instanceId, sessionKey, "assistant", content);
                } else {
                    String errBody = response.body();
                    String errMsg = "Hermes返回错误: HTTP " + response.statusCode();
                    try {
                        JsonNode errJson = objectMapper.readTree(errBody);
                        errMsg += " - " + errJson.path("error").asText("");
                    } catch (Exception ignored) {
                        if (errBody != null && errBody.length() < 200) errMsg += " - " + errBody;
                    }
                    sendError(session, errMsg);
                }
            } catch (java.net.ConnectException e) {
                log.error("Hermes container not reachable on port {} for instance {}", instance.getPort(), instanceId, e);
                sendError(session, "Hermes容器未就绪，请稍后重试 (端口 " + instance.getPort() + ")");
            } catch (Exception e) {
                log.error("Failed to chat with Hermes instance {}", instanceId, e);
                sendError(session, "Hermes连接失败: " + e.getMessage());
            }
        });
    }

    private void connectAndChat(WebSocketSession session, XclawInstance instance,
            String userMessage, String ocSessionKey, Long instanceId) {
        CompletableFuture.runAsync(() -> {
            try {
                int port = instance.getPort();
                log.info("Connecting to OpenClaw gateway at ws://{}:{} sessionKey={}", xclawHost, port, ocSessionKey);

                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
                KeyPair keyPair = keyGen.generateKeyPair();

                byte[] spkiDer = keyPair.getPublic().getEncoded();
                byte[] rawKey = Arrays.copyOfRange(spkiDer, ED25519_SPKI_PREFIX.length, spkiDer.length);
                String deviceId = sha256hex(rawKey);
                String pubKeyB64url = base64urlEncode(rawKey);

                HttpClient httpClient = HttpClient.newHttpClient();
                httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://" + xclawHost + ":" + port),
                        makeOcListener(session, keyPair, deviceId, pubKeyB64url,
                            userMessage, ocSessionKey, instanceId))
                    .get(10, TimeUnit.SECONDS);

            } catch (Exception e) {
                log.error("Failed to connect to OpenClaw instance {}", instanceId, e);
                sendError(session, "连接实例失败: " + e.getMessage());
            }
        });
    }

    private WebSocket.Listener makeOcListener(WebSocketSession session, KeyPair keyPair,
            String deviceId, String pubKeyB64url, String userMessage,
            String ocSessionKey, Long instanceId) {
        return new WebSocket.Listener() {
            private final StringBuilder fullResponse = new StringBuilder();
            private final StringBuilder buf = new StringBuilder();
            private boolean connected = false;
            private boolean done = false;

            @Override
            public void onOpen(java.net.http.WebSocket ws) { ws.request(1); }

            @Override
            public CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence data, boolean last) {
                ws.request(1);
                buf.append(data);
                if (!last) return null;
                String text = buf.toString();
                buf.setLength(0);

                try {
                    JsonNode msg = objectMapper.readTree(text);

                    if ("connect.challenge".equals(msg.path("event").asText())) {
                        String nonce = msg.get("payload").get("nonce").asText();
                        long signedAtMs = System.currentTimeMillis();

                        String v3Payload = String.join("|",
                            "v3", deviceId, "gateway-client", "backend", "operator",
                            "operator.admin", String.valueOf(signedAtMs), "",
                            nonce, "linux", ""
                        );

                        Signature sig = Signature.getInstance("Ed25519");
                        sig.initSign(keyPair.getPrivate());
                        sig.update(v3Payload.getBytes());
                        String sigB64url = base64urlEncode(sig.sign());

                        ObjectNode req = objectMapper.createObjectNode();
                        req.put("type", "req");
                        req.put("id", "1");
                        req.put("method", "connect");
                        ObjectNode params = req.putObject("params");
                        params.put("minProtocol", 3);
                        params.put("maxProtocol", 3);
                        ObjectNode cl = params.putObject("client");
                        cl.put("id", "gateway-client");
                        cl.put("version", "2026.4.15");
                        cl.put("platform", "Linux");
                        cl.put("mode", "backend");
                        params.put("role", "operator");
                        params.putArray("scopes").add("operator.admin");
                        params.putArray("caps");
                        params.putObject("auth");
                        ObjectNode dev = params.putObject("device");
                        dev.put("id", deviceId);
                        dev.put("publicKey", pubKeyB64url);
                        dev.put("signature", sigB64url);
                        dev.put("nonce", nonce);
                        dev.put("signedAt", signedAtMs);
                        ws.sendText(objectMapper.writeValueAsString(req), true);
                        return null;
                    }

                    if ("1".equals(msg.path("id").asText("")) && msg.path("ok").asBoolean(false)) {
                        connected = true;
                        log.info("OpenClaw connect OK for instance {} sessionKey={}", instanceId, ocSessionKey);

                        // Subscribe to the specific session
                        ObjectNode subReq = objectMapper.createObjectNode();
                        subReq.put("type", "req");
                        subReq.put("id", "sub");
                        subReq.put("method", "sessions.subscribe");
                        subReq.putObject("params").put("sessionKey", ocSessionKey);
                        ws.sendText(objectMapper.writeValueAsString(subReq), true);

                        ObjectNode chatReq = objectMapper.createObjectNode();
                        chatReq.put("type", "req");
                        chatReq.put("id", "2");
                        chatReq.put("method", "chat.send");
                        ObjectNode cp = chatReq.putObject("params");
                        cp.put("sessionKey", ocSessionKey);
                        cp.put("message", userMessage);
                        cp.put("idempotencyKey", UUID.randomUUID().toString());
                        ws.sendText(objectMapper.writeValueAsString(chatReq), true);
                        return null;
                    }

                    String event = msg.path("event").asText();
                    JsonNode pl = msg.get("payload");
                    if ("agent".equals(event) && pl != null) {
                        String stream = pl.path("stream").asText();
                        JsonNode eventData = pl.get("data");
                        if ("assistant".equals(stream) && eventData != null) {
                            String delta = eventData.path("delta").asText();
                            if (!delta.isEmpty()) {
                                fullResponse.append(delta);
                                sendChunk(session, delta, false);
                            }
                        } else if ("lifecycle".equals(stream) && eventData != null
                                && "complete".equals(eventData.path("phase").asText())) {
                            finishChat(session, instanceId, ws);
                        }
                        return null;
                    }

                    if ("chat".equals(event) && pl != null
                            && ("final".equals(pl.path("state").asText())
                                || "done".equals(pl.path("state").asText()))) {
                        finishChat(session, instanceId, ws);
                        return null;
                    }

                } catch (Exception e) {
                    log.error("Error processing OpenClaw msg for instance {}", instanceId, e);
                }
                return null;
            }

            private void finishChat(WebSocketSession session, Long instanceId, java.net.http.WebSocket ws) {
                if (done) return;
                done = true;
                log.info("Chat complete for instance {}, response: {}", instanceId, fullResponse.toString());
                sendChunk(session, "", true);
                if (fullResponse.length() > 0) {
                    chatMessageService.saveMessage(instanceId, ocSessionKey, "assistant", fullResponse.toString());
                }
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
            }

            @Override
            public void onError(java.net.http.WebSocket ws, Throwable error) {
                log.error("OpenClaw WS error instance {}: {}", instanceId, error.getMessage());
                sendError(session, "连接实例失败: " + error.getMessage());
            }

            @Override
            public CompletionStage<?> onClose(java.net.http.WebSocket ws, int statusCode, String reason) {
                if (!connected && !done) {
                    sendError(session, "实例连接关闭: " + (reason != null ? reason : ""));
                }
                proxySocketMap.remove(session.getId());
                return null;
            }
        };
    }

    private void sendChunk(WebSocketSession session, String text, boolean done) {
        try {
            ObjectNode resp = objectMapper.createObjectNode();
            resp.put("role", "assistant");
            resp.put("content", text);
            resp.put("done", done);
            if (!done) resp.put("stream", true);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
        } catch (IOException ignored) {}
    }

    private void sendError(WebSocketSession session, String msg) {
        try {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("error", true);
            err.put("content", msg);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(err)));
        } catch (IOException ignored) {}
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionInstanceMap.remove(session.getId());
        java.net.http.WebSocket ws = proxySocketMap.remove(session.getId());
        if (ws != null) {
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, ""); } catch (Exception ignored) {}
        }
    }

    private Long extractInstanceId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            try { return Long.parseLong(parts[3]); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static String base64urlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String sha256hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { return ""; }
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
}
