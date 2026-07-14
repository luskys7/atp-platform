package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "test_tasks")
public class TestTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPlatform platform;

    @Column(name = "script_type", length = 16)
    private String scriptType;

    @Column(name = "script_content", columnDefinition = "LONGTEXT")
    private String scriptContent;

    @Column(name = "script_path", length = 512)
    private String scriptPath;

    @Column(name = "app_package", length = 256)
    private String appPackage;

    @Column(name = "app_path", length = 512)
    private String appPath;

    private Byte priority = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status = TaskStatus.pending;

    @Column(name = "device_ids", columnDefinition = "JSON")
    private String deviceIds = "[]";

    @Column(name = "parallel_count")
    private Integer parallelCount = 1;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 3600;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 0;

    @Column(name = "enable_recording")
    private Boolean enableRecording = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_code", length = 16)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "suite_run_id")
    private Long suiteRunId;
    @Column(name = "suite_run_item_id")
    private Long suiteRunItemId;
    @Column(name = "source_case_id")
    private Long sourceCaseId;
    @Column(name = "resume_from_step")
    private Integer resumeFromStep;
    @Column(name = "parent_task_id")
    private Long parentTaskId;
    @Column(name = "app_package_id")
    private Long appPackageId;
    /** 套件钩子阶段：before / after */
    @Column(name = "suite_hook_phase", length = 16)
    private String suiteHookPhase;
    @Column(name = "env_id")
    private Long envId;
    @Column(name = "dataset_id")
    private Long datasetId;
    @Column(name = "dataset_row_id")
    private Long datasetRowId;
    @Column(name = "variables_json", columnDefinition = "JSON")
    private String variablesJson;

    /** 是否从测试账号池自动占用账号（互斥） */
    @Column(name = "use_account_pool")
    private Boolean useAccountPool = false;

    @Column(name = "test_account_id")
    private Long testAccountId;

    /** 设备标签筛选，逗号分隔，与 Device.tags 匹配 */
    @Column(name = "device_tags", length = 512)
    private String deviceTags;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum TaskPlatform {
        android, ios, both
    }

    public enum TaskStatus {
        pending, queued, running, success, failed, cancelled, timeout, paused, waiting_manual
    }
}
