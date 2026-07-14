package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "control_pool_versions")
public class ControlPoolVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pool_id", nullable = false)
    private Long poolId;

    @Column(name = "version_num", nullable = false)
    private Integer versionNum;

    @Column(name = "env_tag", length = 32)
    private String envTag = "test";

    @Column(name = "requirement_id", length = 128)
    private String requirementId;

    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT", nullable = false)
    private String snapshotJson;

    @Column(name = "change_reason", length = 512)
    private String changeReason;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "screenshot_path", length = 512)
    private String screenshotPath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
