package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "execution_step_markers")
public class ExecutionStepMarker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "recording_id")
    private Long recordingId;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex = 0;

    @Column(name = "step_type", length = 32)
    private String stepType;

    @Column(name = "video_offset_ms", nullable = false)
    private Long videoOffsetMs = 0L;

    @Column(name = "end_offset_ms")
    private Long endOffsetMs;

    @Column(length = 16)
    private String status;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "log_line", length = 512)
    private String logLine;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
