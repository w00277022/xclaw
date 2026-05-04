package com.xclaw.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("xclaw_approval")
public class Approval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long userId;
    private String instanceName;
    private String instanceDescription;
    private String requesterName;
    private String status; // PENDING, APPROVED, REJECTED
    private Long adminId;
    private String adminName;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
