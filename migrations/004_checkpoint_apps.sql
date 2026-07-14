-- TestFlow 断点续跑 + APP 包仓库
USE atp_platform;

CREATE TABLE IF NOT EXISTS suite_runs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    suite_id BIGINT UNSIGNED NOT NULL,
    status ENUM('running','completed','failed','paused') NOT NULL DEFAULT 'running',
    resume_index INT NOT NULL DEFAULT 0,
    total_items INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME NULL,
    INDEX idx_suite (suite_id),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='套件执行批次';

CREATE TABLE IF NOT EXISTS suite_run_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT UNSIGNED NOT NULL,
    case_id BIGINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status ENUM('pending','running','success','failed','skipped') NOT NULL DEFAULT 'pending',
    task_id BIGINT UNSIGNED NULL,
    failed_step_index INT NULL,
    error_message TEXT NULL,
    INDEX idx_run (run_id)
) ENGINE=InnoDB COMMENT='套件执行项';

CREATE TABLE IF NOT EXISTS app_packages (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    package_name VARCHAR(256) NULL,
    version_name VARCHAR(64) NULL,
    version_code VARCHAR(32) NULL,
    platform ENUM('android','ios') NOT NULL DEFAULT 'android',
    file_name VARCHAR(512) NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    md5_hash VARCHAR(64) NOT NULL,
    branch VARCHAR(128) NULL,
    build_log TEXT NULL,
    status ENUM('active','deprecated') NOT NULL DEFAULT 'active',
    uploaded_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_platform (platform),
    INDEX idx_package (package_name)
) ENGINE=InnoDB COMMENT='APP 安装包仓库';

ALTER TABLE test_tasks ADD COLUMN IF NOT EXISTS suite_run_id BIGINT UNSIGNED NULL;
ALTER TABLE test_tasks ADD COLUMN IF NOT EXISTS suite_run_item_id BIGINT UNSIGNED NULL;
ALTER TABLE test_tasks ADD COLUMN IF NOT EXISTS source_case_id BIGINT UNSIGNED NULL;
ALTER TABLE test_tasks ADD COLUMN IF NOT EXISTS resume_from_step INT NULL;
ALTER TABLE test_tasks ADD COLUMN IF NOT EXISTS parent_task_id BIGINT UNSIGNED NULL;
ALTER TABLE test_tasks ADD COLUMN IF NOT EXISTS app_package_id BIGINT UNSIGNED NULL;

ALTER TABLE task_executions ADD COLUMN IF NOT EXISTS failed_step_index INT NULL;
