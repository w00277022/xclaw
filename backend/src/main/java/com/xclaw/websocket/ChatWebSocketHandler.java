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
        String userMessage = payload.has("content") ? payload.get("content").asText() : "";
        if (userMessage.isEmpty()) return;

        chatMessageService.saveMessage(instanceId, "user", userMessage);
        log.info("Chat [{}] {}: {}", instanceId, instance.getName(), userMessage.substring(0, Math.min(50, userMessage.length())));

        connectAndChat(session, instance, userMessage, instanceId);
    }

    private void connectAndChat(WebSocketSession session, XclawInstance instance, String userMessage, Long instanceId) {
        CompletableFuture.runAsync(() -> {
            try {
                int port = instance.getPort();
                log.info("Connecting to OpenClaw gateway at ws://localhost:{}", port);

                // Generate Ed25519 key pair
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
                KeyPair keyPair = keyGen.generateKeyPair();

                // Extract raw public key (32 bytes)
                byte[] spkiDer = keyPair.getPublic().getEncoded();
                byte[] rawKey = Arrays.copyOfRange(spkiDer, ED25519_SPKI_PREFIX.length, spkiDer.length);
                String deviceId = sha256hex(rawKey);
                String pubKeyB64url = base64urlEncode(rawKey);

                HttpClient httpClient = HttpClient.newHttpClient();
                httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + port), makeOcListener(session, keyPair, deviceId, pubKeyB64url, userMessage, instanceId))
                    .get(10, TimeUnit.SECONDS);

            } catch (Exception e) {
                log.error("Failed to connect to OpenClaw instance {}", instanceId, e);
                sendError(session, "连接实例失败: " + e.getMessage());
            }
        });
    }

    private WebSocket.Listener makeOcListener(WebSocketSession session, KeyPair keyPair,
            String deviceId, String pubKeyB64url, String userMessage, Long instanceId) {
        return new WebSocket.Listener() {
            private final StringBuilder fullResponse = new StringBuilder();
            private final StringBuilder buf = new StringBuilder();
            private boolean connected = false;
            private boolean done = false;

            @Override
            public void onOpen(java.net.http.WebSocket ws) {
                ws.request(1);
            }

            @Override
            public CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence data, boolean last) {
                ws.request(1);
                buf.append(data);
                if (!last) return null;
                String text = buf.toString();
                buf.setLength(0);

                try {
                    JsonNode msg = objectMapper.readTree(text);

                    // Connect challenge
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

                    // Connect response
                    if ("1".equals(msg.path("id").asText("")) && msg.path("ok").asBoolean(false)) {
                        connected = true;
                        log.info("OpenClaw connect OK for instance {}", instanceId);

                        // Subscribe then send chat
                        ObjectNode subReq = objectMapper.createObjectNode();
                        subReq.put("type", "req");
                        subReq.put("id", "sub");
                        subReq.put("method", "sessions.subscribe");
                        subReq.putObject("params").put("sessionKey", "agent:main:main");
                        ws.sendText(objectMapper.writeValueAsString(subReq), true);

                        ObjectNode chatReq = objectMapper.createObjectNode();
                        chatReq.put("type", "req");
                        chatReq.put("id", "2");
                        chatReq.put("method", "chat.send");
                        ObjectNode cp = chatReq.putObject("params");
                        cp.put("sessionKey", "agent:main:main");
                        cp.put("message", userMessage);
                        cp.put("idempotencyKey", UUID.randomUUID().toString());
                        ws.sendText(objectMapper.writeValueAsString(chatReq), true);
                        return null;
                    }

                    // Agent event → assistant deltas
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

                    // chat event with state:final or state:done → complete
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
                    chatMessageService.saveMessage(instanceId, "assistant", fullResponse.toString());
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
