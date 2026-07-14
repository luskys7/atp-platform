-- 视频录制 P0 增强：步骤锚点、归档标签
-- 执行前请备份；MySQL 8.0+ 推荐

ALTER TABLE recording_resources
    MODIFY COLUMN task_id BIGINT UNSIGNED NULL COMMENT '关联任务，操作录制可为空';

ALTER TABLE recording_resources
    ADD COLUMN operator_label VARCHAR(128) NULL COMMENT '操作人' AFTER watermark_hash,
    ADD COLUMN thumbnail_path VARCHAR(512) NULL COMMENT '缩略图路径' AFTER operator_label,
    ADD COLUMN module_name VARCHAR(128) NULL COMMENT '业务模块' AFTER thumbnail_path,
    ADD COLUMN version_label VARCHAR(64) NULL COMMENT '迭代版本' AFTER module_name,
    ADD COLUMN project_code VARCHAR(64) NULL COMMENT '项目编码' AFTER version_label,
    ADD COLUMN session_id BIGINT UNSIGNED NULL COMMENT '关联 operation_records.id' AFTER project_code,
    ADD COLUMN watermark_payload VARCHAR(512) NULL COMMENT '水印明文摘要' AFTER watermark_hash,
    ADD COLUMN crop_rect_json VARCHAR(256) NULL COMMENT '选区 JSON' AFTER session_id;

CREATE INDEX idx_rec_module ON recording_resources (module_name);
CREATE INDEX idx_rec_version ON recording_resources (version_label);
CREATE INDEX idx_rec_session ON recording_resources (session_id);

ALTER TABLE operation_records
    MODIFY COLUMN status ENUM('recording','paused','completed','cancelled') NOT NULL DEFAULT 'recording';

ALTER TABLE operation_records
    ADD COLUMN module_name VARCHAR(128) NULL AFTER device_serial,
    ADD COLUMN version_label VARCHAR(64) NULL AFTER module_name,
    ADD COLUMN project_code VARCHAR(64) NULL AFTER version_label,
    ADD COLUMN recording_id BIGINT UNSIGNED NULL COMMENT '关联 recording_resources.id' AFTER project_code;

CREATE INDEX idx_oprec_status ON operation_records (status);
CREATE INDEX idx_oprec_module ON operation_records (module_name);

CREATE TABLE IF NOT EXISTS execution_step_markers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL,
    execution_id BIGINT UNSIGNED NULL,
    device_id BIGINT UNSIGNED NULL,
    recording_id BIGINT UNSIGNED NULL,
    step_index INT NOT NULL DEFAULT 0,
    step_type VARCHAR(32) NULL,
    video_offset_ms BIGINT NOT NULL DEFAULT 0,
    end_offset_ms BIGINT NULL,
    status VARCHAR(16) NULL COMMENT 'ok/fail/skip',
    display_name VARCHAR(128) NULL,
    error_message VARCHAR(512) NULL,
    log_line VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_esm_task (task_id),
    INDEX idx_esm_exec (execution_id),
    INDEX idx_esm_rec (recording_id),
    INDEX idx_esm_step (task_id, step_index)
) ENGINE=InnoDB COMMENT='任务执行步骤与录屏时间锚点';
