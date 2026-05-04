package com.xclaw.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xclaw.entity.ChatMessage;
import com.xclaw.mapper.ChatMessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMessageService extends ServiceImpl<ChatMessageMapper, ChatMessage> {

    public List<ChatMessage> getByInstanceId(Long instanceId) {
        return list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getInstanceId, instanceId)
                .orderByAsc(ChatMessage::getCreatedAt));
    }

    public List<ChatMessage> getHistory(Long instanceId, int limit) {
        return list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getInstanceId, instanceId)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("LIMIT " + limit));
    }

    public ChatMessage saveMessage(Long instanceId, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setInstanceId(instanceId);
        msg.setRole(role);
        msg.setContent(content);
        save(msg);
        return msg;
    }
}
