package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "operation_records")
public class OperationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "device_id", nullable = false)
    private Long deviceId;
    @Column(name = "device_serial", length = 128)
    private String deviceSerial;

    @Column(name = "module_name", length = 128)
    private String moduleName;

    @Column(name = "version_label", length = 64)
    private String versionLabel;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    @Column(name = "recording_id")
    private Long recordingId;

    @Column(name = "steps_json", columnDefinition = "LONGTEXT")
    private String stepsJson;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.recording;
    @Column(name = "created_by")
    private Long createdBy;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public enum RecordStatus { recording, paused, completed, cancelled }
}
