-- M3 控件池平台治理：四维绑定 + archived 状态
USE atp_platform;

ALTER TABLE control_pools
    ADD COLUMN IF NOT EXISTS team_id BIGINT UNSIGNED NULL COMMENT '团队/项目 ID' AFTER element_name,
    ADD COLUMN IF NOT EXISTS env_tag VARCHAR(64) NOT NULL DEFAULT '' COMMENT '环境标签' AFTER version_tag;

-- 扩展 status 枚举（MySQL 需 MODIFY）
ALTER TABLE control_pools
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'active';

CREATE INDEX IF NOT EXISTS idx_control_pools_dims
    ON control_pools (app_package, element_name, version_tag, env_tag, status);
