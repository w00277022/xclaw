package com.xclaw.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("xclaw_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String displayName;
    private String role; // ADMIN, USER
    private Boolean canCreateOpenclaw = true;  // 可创建 OpenClaw 实例
    private Boolean canCreateHermes = false;   // 可创建 Hermes-Agent 实例
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
