package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "private_control_bindings")
public class PrivateControlBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "step_index")
    private Integer stepIndex;

    @Column(name = "element_name", length = 256)
    private String elementName;

    @Column(name = "locator_type", length = 16)
    private String locatorType;

    @Column(name = "locator_value", columnDefinition = "TEXT")
    private String locatorValue;

    @Column(name = "pool_id")
    private Long poolId;

    @Column(name = "is_isolated")
    private Boolean isIsolated = true;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
