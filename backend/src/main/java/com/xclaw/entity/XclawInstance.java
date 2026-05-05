package com.xclaw.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("xclaw_instance")
public class XclawInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long userId;
    private String containerId;
    private String status; // CREATING, RUNNING, STOPPED, ERROR, PENDING_APPROVAL, REJECTED
    private Integer port;
    private String configJson;
    private String type; // openclaw, hermes
    private String description;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
