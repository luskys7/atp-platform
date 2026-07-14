package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "data_factory_runs")
public class DataFactoryRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "suite_run_id")
    private Long suiteRunId;

    @Column(name = "created_vars_json", columnDefinition = "TEXT")
    private String createdVarsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleanup_status", length = 16)
    private CleanupStatus cleanupStatus = CleanupStatus.pending;

    @Column(name = "cleanup_message", columnDefinition = "TEXT")
    private String cleanupMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "cleaned_at")
    private LocalDateTime cleanedAt;

    public enum CleanupStatus { pending, done, failed, skipped }
}
