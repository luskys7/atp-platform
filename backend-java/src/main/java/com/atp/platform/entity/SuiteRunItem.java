package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "suite_run_items")
public class SuiteRunItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "run_id", nullable = false)
    private Long runId;
    @Column(name = "case_id", nullable = false)
    private Long caseId;
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemStatus status = ItemStatus.pending;
    @Column(name = "task_id")
    private Long taskId;
    @Column(name = "failed_step_index")
    private Integer failedStepIndex;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum ItemStatus { pending, running, success, failed, skipped }
}
