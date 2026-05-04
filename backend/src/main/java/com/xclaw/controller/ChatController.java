package com.xclaw.controller;

import com.xclaw.entity.ChatMessage;
import com.xclaw.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;

    @GetMapping("/{instanceId}/history")
    public ResponseEntity<List<ChatMessage>> history(@PathVariable Long instanceId) {
        return ResponseEntity.ok(chatMessageService.getByInstanceId(instanceId));
    }
}
