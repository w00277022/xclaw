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
    private String containerId;
    private String status; // CREATING, RUNNING, STOPPED, ERROR, DELETING
    private Integer port;
    private String configJson;
    private String description;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
