package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "recording_resources")
public class RecordingResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "file_name", length = 256)
    private String fileName;

    @Column(name = "file_path", length = 512)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize = 0L;

    @Column(name = "duration_seconds")
    private Integer durationSeconds = 0;

    @Column(length = 16)
    private String format = "mp4";

    @Column(name = "watermark_hash", length = 64)
    private String watermarkHash;

    @Column(name = "watermark_payload", length = 512)
    private String watermarkPayload;

    @Column(name = "operator_label", length = 128)
    private String operatorLabel;

    @Column(name = "thumbnail_path", length = 512)
    private String thumbnailPath;

    @Column(name = "module_name", length = 128)
    private String moduleName;

    @Column(name = "version_label", length = 64)
    private String versionLabel;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "crop_rect_json", length = 256)
    private String cropRectJson;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private RecordingStatus status = RecordingStatus.recording;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum RecordingStatus {
        recording, uploading, ready, failed
    }
}
