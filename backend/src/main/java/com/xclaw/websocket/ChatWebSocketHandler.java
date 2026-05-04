package com.xclaw.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final XclawInstanceService instanceService;
    private final ChatMessageService chatMessageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openclaw.backend.url}")
    private String backendUrl;

    @Value("${openclaw.backend.api-key:}")
    private String apiKey;

    @Value("${openclaw.backend.model}")
    private String model;

    public ChatWebSocketHandler(XclawInstanceService instanceService, ChatMessageService chatMessageService) {
        this.instanceService = instanceService;
        this.chatMessageService = chatMessageService;
    }

    private final Map<String, Long> sessionInstanceMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long instanceId = extractInstanceId(session);
        if (instanceId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        sessionInstanceMap.put(session.getId(), instanceId);
        log.info("WebSocket connected for instance {}, model={}", instanceId, model);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long instanceId = sessionInstanceMap.get(session.getId());
        if (instanceId == null) return;

        XclawInstance instance = instanceService.getById(instanceId);
        if (instance == null || !"RUNNING".equals(instance.getStatus())) {
            session.sendMessage(new TextMessage("{\"error\":\"Instance not running\"}"));
            return;
        }

        JsonNode payload = objectMapper.readTree(message.getPayload());
        String userMessage = payload.has("content") ? payload.get("content").asText() : "";
        if (userMessage.isEmpty()) return;

        // Save user message
        chatMessageService.saveMessage(instanceId, "user", userMessage);

        // Forward to instance's own OpenClaw gateway or unified backend
        String llmUrl;
        if (instance.getPort() != null && instance.getPort() >= 9200) {
            // Independent OpenClaw instance - use its gateway
            llmUrl = "http://localhost:" + instance.getPort() + "/v1/chat/completions";
        } else {
            llmUrl = backendUrl + "/v1/chat/completions";
        }
        log.info("Chat for instance {} → {}", instanceId, llmUrl);

        // Build conversation history from DB
        List<ChatMessage> history = chatMessageService.getHistory(instanceId, 20);
        ArrayNode messages = objectMapper.createArrayNode();
        // Add system prompt
        ObjectNode sysMsg = objectMapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", "You are a helpful AI assistant.");
        messages.add(sysMsg);
        // Add history
        for (ChatMessage msg : history) {
            ObjectNode m = objectMapper.createObjectNode();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }

        // Build request body
        ObjectNode reqBody = objectMapper.createObjectNode();
        reqBody.put("model", model);
        reqBody.set("messages", messages);
        reqBody.put("stream", true);

        // Call LLM API with streaming in async thread
        String finalUserMessage = userMessage;
        CompletableFuture.runAsync(() -> {
            StringBuilder fullContent = new StringBuilder();
            HttpURLConnection conn = null;
            try {
                URI uri = URI.create(llmUrl);
                conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(120000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(objectMapper.writeValueAsBytes(reqBody));
                    os.flush();
                }

                int status = conn.getResponseCode();
                if (status != 200) {
                    // Read error body
                    String errBody = new String(conn.getErrorStream().readAllBytes());
                    log.error("LLM API error {}: {}", status, errBody);
                    ObjectNode errorResp = objectMapper.createObjectNode();
                    errorResp.put("role", "assistant");
                    errorResp.put("content", "❌ LLM API 返回错误 (" + status + "): " + errBody);
                    errorResp.put("error", true);
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResp)));
                    return;
                }

                // Read SSE stream
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ") && !"data: [DONE]".equals(line)) {
                            String jsonData = line.substring(6);
                            try {
                                JsonNode chunk = objectMapper.readTree(jsonData);
                                JsonNode choices = chunk.get("choices");
                                if (choices != null && choices.isArray() && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null) {
                                        JsonNode content = delta.get("content");
                                        if (content != null && !content.asText().isEmpty()) {
                                            String text = content.asText();
                                            fullContent.append(text);
                                            // Send chunk to frontend
                                            ObjectNode chunkResp = objectMapper.createObjectNode();
                                            chunkResp.put("role", "assistant");
                                            chunkResp.put("content", text);
                                            chunkResp.put("stream", true);
                                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chunkResp)));
                                        }
                                    }
                                }
                            } catch (Exception ignore) {}
                        }
                    }
                }

                // Send completion signal
                ObjectNode doneResp = objectMapper.createObjectNode();
                doneResp.put("role", "assistant");
                doneResp.put("content", "");
                doneResp.put("done", true);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(doneResp)));

                // Save assistant message
                if (fullContent.length() > 0) {
                    chatMessageService.saveMessage(instanceId, "assistant", fullContent.toString());
                }

            } catch (Exception e) {
                log.error("Error calling LLM API for instance {}", instanceId, e);
                try {
                    ObjectNode errorResp = objectMapper.createObjectNode();
                    errorResp.put("role", "assistant");
                    errorResp.put("content", "❌ 对话失败: " + e.getMessage());
                    errorResp.put("error", true);
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResp)));
                } catch (Exception ignore) {}
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionInstanceMap.remove(session.getId());
        log.info("WebSocket closed: {}", status);
    }

    private Long extractInstanceId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            try { return Long.parseLong(parts[3]); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
