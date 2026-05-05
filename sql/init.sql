-- XClaw 管理平台数据库初始化
CREATE DATABASE IF NOT EXISTS xclaw_platform DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE xclaw_platform;

DROP TABLE IF EXISTS xclaw_instance;
CREATE TABLE xclaw_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '实例名称',
    container_id VARCHAR(100) DEFAULT NULL COMMENT 'Docker容器ID',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATING' COMMENT '状态: CREATING/RUNNING/STOPPED/ERROR/DELETING',
    port INT DEFAULT NULL COMMENT '映射端口',
    config_json TEXT DEFAULT NULL COMMENT '配置JSON',
    description VARCHAR(500) DEFAULT NULL COMMENT '描述',
    error_msg VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    type VARCHAR(20) NOT NULL DEFAULT 'openclaw' COMMENT '类型: openclaw/hermes',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_container_id (container_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XClaw实例表';

DROP TABLE IF EXISTS xclaw_chat_message;
CREATE TABLE xclaw_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT NOT NULL COMMENT '实例ID',
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant',
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instance_id (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录表';
