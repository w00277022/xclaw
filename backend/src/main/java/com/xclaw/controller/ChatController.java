package com.xclaw.controller;

import com.xclaw.entity.ChatMessage;
import com.xclaw.entity.XclawInstance;
import com.xclaw.service.ChatMessageService;
import com.xclaw.service.XclawInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final XclawInstanceService instanceService;

    @GetMapping("/{instanceId}/history")
    public ResponseEntity<?> history(@PathVariable Long instanceId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");

        if (userId != null && !"ADMIN".equals(role)) {
            XclawInstance instance = instanceService.getById(instanceId);
            if (instance == null || !userId.equals(instance.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权访问此实例"));
            }
        }

        return ResponseEntity.ok(chatMessageService.getByInstanceId(instanceId));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long messageId, HttpServletRequest request) {
        ChatMessage msg = chatMessageService.getById(messageId);
        if (msg == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "消息不存在"));
        }

        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        if (userId != null && !"ADMIN".equals(role)) {
            XclawInstance instance = instanceService.getById(msg.getInstanceId());
            if (instance == null || !userId.equals(instance.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权删除"));
            }
        }

        chatMessageService.removeById(messageId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        try {
            String uploadDir = System.getProperty("java.io.tmpdir") + "/xclaw-uploads";
            Files.createDirectories(Paths.get(uploadDir));

            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf("."))
                    : "";
            String newName = UUID.randomUUID().toString() + ext;
            Path dest = Paths.get(uploadDir, newName);
            file.transferTo(dest.toFile());

            return ResponseEntity.ok(Map.of(
                "fileName", originalName,
                "fileKey", newName,
                "size", file.getSize(),
                "mimeType", file.getContentType()
            ));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "上传失败: " + e.getMessage()));
        }
    }
}
