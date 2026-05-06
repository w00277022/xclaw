package com.xclaw.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("xclaw_node")
public class XclawNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private Integer port;
    private String sshUser;
    private String sshKey;
    private String status; // ONLINE, OFFLINE
    private Boolean isLocal;
    private LocalDateTime createdAt;
}
