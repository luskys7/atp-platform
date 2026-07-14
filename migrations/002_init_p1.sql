-- 自动化测试平台 P1 二期新增表
USE atp_platform;

-- 全局控件池主表
CREATE TABLE IF NOT EXISTS control_pools (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    pool_key VARCHAR(128) NOT NULL COMMENT '控件池唯一键(哈希)',
    app_package VARCHAR(256) NOT NULL COMMENT '应用包名',
    page_name VARCHAR(256) NOT NULL DEFAULT '' COMMENT '页面标识',
    element_name VARCHAR(256) NOT NULL COMMENT '控件语义名称',
    platform ENUM('android','ios','both') NOT NULL DEFAULT 'both',
    locator_type ENUM('id','xpath','accessibility','ai','image') NOT NULL DEFAULT 'id',
    locator_value TEXT NOT NULL COMMENT '定位表达式',
    feature_vector JSON NULL COMMENT '多维控件特征向量',
    hit_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '命中次数',
    version_tag VARCHAR(64) NOT NULL DEFAULT '' COMMENT '版本标签',
    status ENUM('active','deprecated','blocked') NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pool_key (pool_key),
    INDEX idx_app_package (app_package),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='全局控件池主表';

-- 步骤私有控件绑定表
CREATE TABLE IF NOT EXISTS private_control_bindings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL COMMENT '用例/任务ID',
    step_index INT NOT NULL COMMENT '步骤序号',
    element_name VARCHAR(256) NOT NULL COMMENT '控件语义名称',
    locator_type ENUM('id','xpath','accessibility','ai','image','pool') NOT NULL DEFAULT 'pool',
    locator_value TEXT NOT NULL COMMENT '私有定位表达式或池引用',
    pool_id BIGINT UNSIGNED NULL COMMENT '关联控件池ID',
    is_isolated TINYINT NOT NULL DEFAULT 1 COMMENT '是否隔离(不全局生效)',
    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_step_element (task_id, step_index, element_name),
    INDEX idx_task_id (task_id),
    INDEX idx_pool_id (pool_id)
) ENGINE=InnoDB COMMENT='步骤私有控件绑定表';

-- 控件变更溯源日志表
CREATE TABLE IF NOT EXISTS control_change_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    pool_id BIGINT UNSIGNED NULL,
    binding_id BIGINT UNSIGNED NULL,
    change_type ENUM('create','update','deprecate','rollback','dirty_reject') NOT NULL,
    before_value JSON NULL,
    after_value JSON NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    reason VARCHAR(512) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pool_id (pool_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='控件变更溯源日志表';

-- 用例自愈记录表
CREATE TABLE IF NOT EXISTS case_healing_records (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL,
    execution_id BIGINT UNSIGNED NULL,
    step_index INT NOT NULL,
    original_locator TEXT NOT NULL,
    healed_locator TEXT NOT NULL,
    heal_strategy ENUM('pool_fallback','ai_locate','snapshot_diff') NOT NULL,
    success TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB COMMENT='用例自愈记录表';
