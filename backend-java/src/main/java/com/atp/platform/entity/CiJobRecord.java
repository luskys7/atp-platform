package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ci_job_records")
public class CiJobRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(length = 32)
    private String source = "jenkins";

    @Column(name = "job_name", length = 256)
    private String jobName;

    @Column(name = "build_number", length = 64)
    private String buildNumber;

    @Column(name = "callback_url", length = 512)
    private String callbackUrl;

    @Column(length = 32)
    private String status = "triggered";

    @Column(name = "callback_status")
    private Integer callbackStatus;

    @Column(name = "callback_response", columnDefinition = "TEXT")
    private String callbackResponse;

    @Column(name = "callback_at")
    private LocalDateTime callbackAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
