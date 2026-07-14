package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "locator_failure_records")
public class LocatorFailureRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "pool_id")
    private Long poolId;

    @Column(name = "element_name", length = 256)
    private String elementName;

    @Column(name = "failure_class", length = 32)
    private String failureClass = "timing";

    @Column(name = "device_meta", columnDefinition = "JSON")
    private String deviceMeta;

    @Column(name = "attempts_json", columnDefinition = "JSON")
    private String attemptsJson;

    @Column(name = "app_package", length = 256)
    private String appPackage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
