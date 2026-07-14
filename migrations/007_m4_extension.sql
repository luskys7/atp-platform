-- M4 扩展：核心控件标记、失败记录
USE atp_platform;

ALTER TABLE control_pools
    ADD COLUMN IF NOT EXISTS is_core TINYINT NOT NULL DEFAULT 0 COMMENT '是否核心控件' AFTER env_tag,
    ADD COLUMN IF NOT EXISTS control_tag VARCHAR(32) NOT NULL DEFAULT 'static' COMMENT '控件分级标签' AFTER is_core;

CREATE TABLE IF NOT EXISTS locator_failure_records (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NULL,
    execution_id BIGINT UNSIGNED NULL,
    pool_id BIGINT UNSIGNED NULL,
    element_name VARCHAR(256) NOT NULL DEFAULT '',
    failure_class VARCHAR(32) NOT NULL DEFAULT 'timing' COMMENT 'timing/device/permanent',
    device_meta JSON NULL,
    attempts_json JSON NULL,
    app_package VARCHAR(256) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_failure_class (failure_class),
    INDEX idx_element_name (element_name),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='定位失败分层记录';
