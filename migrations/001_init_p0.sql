-- 自动化测试平台 P0 首期建表
-- 字符集: utf8mb4

CREATE DATABASE IF NOT EXISTS atp_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE atp_platform;

-- 账号权限表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT 'bcrypt密码哈希',
    display_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '显示名称',
    role ENUM('super_admin','test_admin','tester','developer_readonly') NOT NULL DEFAULT 'tester' COMMENT '角色',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role (role),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='账号权限表';

-- 操作审计表
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(64) NOT NULL COMMENT '操作类型',
    resource_type VARCHAR(64) NOT NULL COMMENT '资源类型',
    resource_id VARCHAR(128) NOT NULL DEFAULT '',
    detail JSON NULL COMMENT '操作详情',
    ip VARCHAR(64) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='操作审计表';

-- 设备白名单表
CREATE TABLE IF NOT EXISTS device_whitelist (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    serial_number VARCHAR(128) NOT NULL UNIQUE COMMENT '设备序列号/UDID',
    platform ENUM('android','ios') NOT NULL,
    remark VARCHAR(255) NOT NULL DEFAULT '',
    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_platform (platform)
) ENGINE=InnoDB COMMENT='设备白名单';

-- 设备信息表
CREATE TABLE IF NOT EXISTS devices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    serial_number VARCHAR(128) NOT NULL UNIQUE COMMENT '设备序列号/UDID',
    name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '设备名称',
    platform ENUM('android','ios') NOT NULL,
    os_version VARCHAR(32) NOT NULL DEFAULT '' COMMENT '系统版本',
    model VARCHAR(128) NOT NULL DEFAULT '' COMMENT '机型',
    status ENUM('online','offline','busy','maintenance','error') NOT NULL DEFAULT 'offline' COMMENT '设备状态',
    agent_host VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Agent所在主机',
    agent_port INT NOT NULL DEFAULT 0,
    battery_level TINYINT UNSIGNED NOT NULL DEFAULT 0,
    screen_width INT NOT NULL DEFAULT 0,
    screen_height INT NOT NULL DEFAULT 0,
    wda_port INT NOT NULL DEFAULT 0 COMMENT 'iOS WDA端口',
    adb_port INT NOT NULL DEFAULT 5037 COMMENT 'ADB端口',
    last_heartbeat_at DATETIME NULL COMMENT '最后心跳时间',
    locked_by_task_id BIGINT UNSIGNED NULL COMMENT '当前占用任务ID',
    lock_expires_at DATETIME NULL COMMENT '锁过期时间',
    is_whitelisted TINYINT NOT NULL DEFAULT 0 COMMENT '是否在白名单',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_platform (platform),
    INDEX idx_locked_by (locked_by_task_id)
) ENGINE=InnoDB COMMENT='设备信息表';

-- 测试任务表
CREATE TABLE IF NOT EXISTS test_tasks (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(256) NOT NULL COMMENT '任务名称',
    description TEXT NULL,
    platform ENUM('android','ios','both') NOT NULL DEFAULT 'android',
    script_type ENUM('appium','python') NOT NULL DEFAULT 'python',
    script_content LONGTEXT NOT NULL COMMENT '脚本内容',
    script_path VARCHAR(512) NOT NULL DEFAULT '' COMMENT '脚本文件路径',
    app_package VARCHAR(256) NOT NULL DEFAULT '' COMMENT 'Android包名/iOS BundleId',
    app_path VARCHAR(512) NOT NULL DEFAULT '' COMMENT '安装包路径',
    priority TINYINT NOT NULL DEFAULT 5 COMMENT '优先级1-10,数字越小越高',
    status ENUM('pending','queued','running','success','failed','cancelled','timeout') NOT NULL DEFAULT 'pending',
    device_ids JSON NULL COMMENT '指定设备ID列表',
    parallel_count INT NOT NULL DEFAULT 1 COMMENT '并行设备数',
    timeout_seconds INT NOT NULL DEFAULT 3600 COMMENT '超时秒数',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retries INT NOT NULL DEFAULT 0 COMMENT '最大重试',
    enable_recording TINYINT NOT NULL DEFAULT 1 COMMENT '是否录屏',
    created_by BIGINT UNSIGNED NOT NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    error_code VARCHAR(16) NULL COMMENT '错误码 E1xxx/E2xxx/E3xxx',
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='测试任务表';

-- 任务执行实例表（并行拆分）
CREATE TABLE IF NOT EXISTS task_executions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    status ENUM('pending','running','success','failed','cancelled','timeout') NOT NULL DEFAULT 'pending',
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    result_summary TEXT NULL,
    error_code VARCHAR(16) NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_device_id (device_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='任务执行实例表';

-- 录屏资源表
CREATE TABLE IF NOT EXISTS recording_resources (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL,
    execution_id BIGINT UNSIGNED NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    file_name VARCHAR(256) NOT NULL,
    file_path VARCHAR(512) NOT NULL COMMENT 'MinIO对象路径',
    file_size BIGINT UNSIGNED NOT NULL DEFAULT 0,
    duration_seconds INT NOT NULL DEFAULT 0,
    format VARCHAR(16) NOT NULL DEFAULT 'mp4',
    watermark_hash VARCHAR(64) NOT NULL DEFAULT '' COMMENT '防篡改水印哈希',
    status ENUM('recording','uploading','ready','failed') NOT NULL DEFAULT 'recording',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_device_id (device_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='录屏资源表';

-- 执行日志表
CREATE TABLE IF NOT EXISTS execution_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL,
    execution_id BIGINT UNSIGNED NULL,
    device_id BIGINT UNSIGNED NULL,
    log_type ENUM('system','app_crash','script','platform') NOT NULL DEFAULT 'script',
    level ENUM('debug','info','warn','error') NOT NULL DEFAULT 'info',
    message TEXT NOT NULL,
    stack_trace TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_execution_id (execution_id),
    INDEX idx_log_type (log_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='执行日志表';

-- 测试报告表
CREATE TABLE IF NOT EXISTS test_reports (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL UNIQUE,
    title VARCHAR(256) NOT NULL,
    total_executions INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    pass_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    summary TEXT NULL,
    pdf_path VARCHAR(512) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id)
) ENGINE=InnoDB COMMENT='测试报告表';

-- 默认超级管理员 (密码: admin123, bcrypt)
INSERT INTO users (username, password_hash, display_name, role) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '超级管理员', 'super_admin')
ON DUPLICATE KEY UPDATE username=username;
