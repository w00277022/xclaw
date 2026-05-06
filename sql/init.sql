-- XClaw 管理平台数据库初始化
CREATE DATABASE IF NOT EXISTS xclaw_platform DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE xclaw_platform;

DROP TABLE IF EXISTS xclaw_approval;
DROP TABLE IF EXISTS xclaw_chat_message;
DROP TABLE IF EXISTS xclaw_instance;
DROP TABLE IF EXISTS xclaw_user;
DROP TABLE IF EXISTS xclaw_node;

CREATE TABLE xclaw_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    display_name VARCHAR(100) DEFAULT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    can_create_openclaw TINYINT(1) NOT NULL DEFAULT 1,
    can_create_hermes TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE xclaw_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '节点名称',
    host VARCHAR(200) NOT NULL DEFAULT 'localhost' COMMENT '主机地址',
    port INT NOT NULL DEFAULT 22 COMMENT 'SSH端口',
    ssh_user VARCHAR(100) DEFAULT NULL COMMENT 'SSH用户名',
    ssh_key TEXT DEFAULT NULL COMMENT 'SSH私钥',
    status VARCHAR(30) NOT NULL DEFAULT 'ONLINE' COMMENT '状态: ONLINE/OFFLINE',
    is_local TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否本机节点',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent节点表';

CREATE TABLE xclaw_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '实例名称',
    user_id BIGINT DEFAULT NULL COMMENT '创建者用户ID',
    container_id VARCHAR(100) DEFAULT NULL COMMENT '容器/进程ID',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATING' COMMENT '状态: CREATING/RUNNING/STOPPED/ERROR',
    port INT DEFAULT NULL COMMENT '映射端口',
    config_json TEXT DEFAULT NULL COMMENT '配置JSON',
    description VARCHAR(500) DEFAULT NULL COMMENT '描述',
    error_msg VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    type VARCHAR(20) NOT NULL DEFAULT 'openclaw' COMMENT '类型: openclaw/hermes',
    node_id BIGINT DEFAULT NULL COMMENT '部署节点ID',
    gateway_token VARCHAR(255) DEFAULT NULL COMMENT 'Gateway访问Token',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_container_id (container_id),
    INDEX idx_node_id (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XClaw实例表';

CREATE TABLE xclaw_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT NOT NULL COMMENT '实例ID',
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant',
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instance_id (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录表';

CREATE TABLE xclaw_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    instance_name VARCHAR(100),
    instance_description VARCHAR(500),
    requester_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_id BIGINT,
    admin_name VARCHAR(100),
    reject_reason VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批表';

-- 插入默认本机节点
INSERT INTO xclaw_node (name, host, port, status, is_local) VALUES ('本机节点', 'localhost', 22, 'ONLINE', 1);
