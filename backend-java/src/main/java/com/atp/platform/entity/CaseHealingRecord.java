package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "case_healing_records")
public class CaseHealingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "step_index")
    private Integer stepIndex;

    @Column(name = "original_locator", columnDefinition = "TEXT")
    private String originalLocator;

    @Column(name = "healed_locator", columnDefinition = "TEXT")
    private String healedLocator;

    @Enumerated(EnumType.STRING)
    @Column(name = "heal_strategy", length = 32)
    private HealStrategy healStrategy;

    private Boolean success = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum HealStrategy {
        pool_fallback, ai_locate, snapshot_diff, ocr_fallback
    }
}
