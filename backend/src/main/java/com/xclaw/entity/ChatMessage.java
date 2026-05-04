package com.xclaw.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("xclaw_chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private String role; // user / assistant
    private String content;
    private LocalDateTime createdAt;
}
