package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "common_steps")
public class CommonStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 128)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "steps_content", columnDefinition = "LONGTEXT", nullable = false)
    private String stepsContent;
    @Column(name = "input_params", columnDefinition = "JSON")
    private String inputParams;
    @Column(name = "output_params", columnDefinition = "JSON")
    private String outputParams;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StepStatus status = StepStatus.active;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum StepStatus { active, deprecated }
}
