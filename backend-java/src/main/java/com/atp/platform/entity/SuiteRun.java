package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "suite_runs")
public class SuiteRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "suite_id", nullable = false)
    private Long suiteId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status = RunStatus.running;
    @Column(name = "resume_index")
    private Integer resumeIndex = 0;
    @Column(name = "total_items")
    private Integer totalItems = 0;
    @Column(name = "success_count")
    private Integer successCount = 0;
    @Column(name = "failed_count")
    private Integer failedCount = 0;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "started_at")
    private LocalDateTime startedAt = LocalDateTime.now();
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    @Column(name = "config_snapshot_json", columnDefinition = "LONGTEXT")
    private String configSnapshotJson;
    @Column(name = "hook_after_triggered")
    private Boolean hookAfterTriggered = false;

    public enum RunStatus { running, completed, failed, paused }
}
