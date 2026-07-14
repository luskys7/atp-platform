-- TestFlow 平台核心 P0 模块
USE atp_platform;

CREATE TABLE IF NOT EXISTS case_folders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT UNSIGNED NULL,
    name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB COMMENT='用例目录';

CREATE TABLE IF NOT EXISTS test_cases (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    folder_id BIGINT UNSIGNED NULL,
    name VARCHAR(256) NOT NULL,
    preconditions TEXT NULL,
    expected_result TEXT NULL,
    steps_content LONGTEXT NULL COMMENT 'visual JSON 或脚本',
    script_type VARCHAR(16) NOT NULL DEFAULT 'visual',
    platform ENUM('android','ios','both') NOT NULL DEFAULT 'android',
    app_package VARCHAR(256) NULL,
    priority TINYINT NOT NULL DEFAULT 3,
    tags VARCHAR(512) NULL,
    case_status ENUM('draft','review','active','deprecated') NOT NULL DEFAULT 'draft',
    version_num INT NOT NULL DEFAULT 1,
    owner_id BIGINT UNSIGNED NULL,
    requirement_id VARCHAR(64) NULL,
    defect_id VARCHAR(64) NULL,
    env_id BIGINT UNSIGNED NULL,
    enable_recording TINYINT NOT NULL DEFAULT 1,
    timeout_seconds INT NOT NULL DEFAULT 3600,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_folder (folder_id),
    INDEX idx_status (case_status),
    INDEX idx_deleted (deleted_at)
) ENGINE=InnoDB COMMENT='测试用例资产';

CREATE TABLE IF NOT EXISTS test_case_versions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT UNSIGNED NOT NULL,
    version_num INT NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    change_note VARCHAR(512) NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_case (case_id)
) ENGINE=InnoDB COMMENT='用例版本历史';

CREATE TABLE IF NOT EXISTS common_steps (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    description TEXT NULL,
    steps_content LONGTEXT NOT NULL,
    input_params JSON NULL,
    output_params JSON NULL,
    status ENUM('active','deprecated') NOT NULL DEFAULT 'active',
    created_by BIGINT UNSIGNED NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='公共步骤库';

CREATE TABLE IF NOT EXISTS test_suites (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description TEXT NULL,
    tags VARCHAR(512) NULL,
    env_id BIGINT UNSIGNED NULL,
    exec_mode ENUM('serial','parallel') NOT NULL DEFAULT 'serial',
    fail_policy ENUM('continue','stop') NOT NULL DEFAULT 'continue',
    hook_before TEXT NULL,
    hook_after TEXT NULL,
    created_by BIGINT UNSIGNED NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='测试套件';

CREATE TABLE IF NOT EXISTS test_suite_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    suite_id BIGINT UNSIGNED NOT NULL,
    case_id BIGINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_suite_case (suite_id, case_id),
    INDEX idx_suite (suite_id)
) ENGINE=InnoDB COMMENT='套件用例编排';

CREATE TABLE IF NOT EXISTS test_environments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    env_type ENUM('test','staging','gray','prod') NOT NULL DEFAULT 'test',
    base_url VARCHAR(512) NULL,
    config_json JSON NULL COMMENT '环境变量键值',
    description TEXT NULL,
    status ENUM('active','disabled') NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='环境配置中心';

CREATE TABLE IF NOT EXISTS data_sets (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    env_id BIGINT UNSIGNED NULL,
    description TEXT NULL,
    columns_json JSON NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='数据集';

CREATE TABLE IF NOT EXISTS data_set_rows (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    dataset_id BIGINT UNSIGNED NOT NULL,
    row_data_json JSON NOT NULL,
    lock_status ENUM('idle','busy') NOT NULL DEFAULT 'idle',
    locked_by_task_id BIGINT UNSIGNED NULL,
    INDEX idx_dataset (dataset_id),
    INDEX idx_lock (lock_status)
) ENGINE=InnoDB COMMENT='数据集行';

CREATE TABLE IF NOT EXISTS scheduled_jobs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    suite_id BIGINT UNSIGNED NOT NULL,
    cron_expression VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    priority TINYINT NOT NULL DEFAULT 5,
    last_triggered_at DATETIME NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB COMMENT='定时调度任务';

CREATE TABLE IF NOT EXISTS recycle_bin_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL,
    resource_name VARCHAR(256) NOT NULL,
    snapshot_json LONGTEXT NULL,
    deleted_by BIGINT UNSIGNED NULL,
    deleted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at DATETIME NOT NULL,
    INDEX idx_type (resource_type),
    INDEX idx_expire (expire_at)
) ENGINE=InnoDB COMMENT='资产回收站';

CREATE TABLE IF NOT EXISTS operation_records (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT UNSIGNED NOT NULL,
    device_serial VARCHAR(128) NULL,
    steps_json LONGTEXT NULL,
    status ENUM('recording','completed','cancelled') NOT NULL DEFAULT 'recording',
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME NULL
) ENGINE=InnoDB COMMENT='操作录制会话';
