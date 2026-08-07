package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ci_config")
public class CiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_token", nullable = false, length = 128)
    private String webhookToken;

    private Boolean enabled = true;

    @Column(name = "default_platform", length = 16)
    private String defaultPlatform = "android";

    @Column(name = "default_script_type", length = 16)
    private String defaultScriptType = "python";

    @Column(name = "default_script_content", columnDefinition = "TEXT")
    private String defaultScriptContent = "print('CI triggered task')";

    @Column(name = "default_app_package", length = 256)
    private String defaultAppPackage;

    @Column(name = "default_timeout_seconds")
    private Integer defaultTimeoutSeconds = 3600;

    @Column(name = "auto_submit")
    private Boolean autoSubmit = true;

    /** 逗号/换行分隔的 Jenkins 服务器 IP 白名单，空表示不限制 */
    @Column(name = "ip_whitelist", columnDefinition = "TEXT")
    private String ipWhitelist;

    /** 允许触发的分支，逗号/换行分隔；空表示不限制 */
    @Column(name = "branch_allow", columnDefinition = "TEXT")
    private String branchAllow;

    /** 禁止触发的分支，逗号/换行分隔 */
    @Column(name = "branch_deny", columnDefinition = "TEXT")
    private String branchDeny;

    /** 同一 CI 链路最多同时运行的自动化任务数 */
    @Column(name = "max_concurrent_tasks")
    private Integer maxConcurrentTasks = 3;

    /** 接收失败时平台重试次数 */
    @Column(name = "receive_retry_count")
    private Integer receiveRetryCount = 0;

    /** 消息接收超时时间（秒） */
    @Column(name = "receive_timeout_seconds")
    private Integer receiveTimeoutSeconds = 30;

    /** 默认绑定的回归测试套件 */
    @Column(name = "default_suite_id")
    private Long defaultSuiteId;

    /** 默认绑定的版本基线 */
    @Column(name = "default_baseline_id")
    private Long defaultBaselineId;

    /** 默认执行环境 */
    @Column(name = "default_env_id")
    private Long defaultEnvId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
