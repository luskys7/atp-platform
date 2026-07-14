package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "version_baselines")
public class VersionBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "version_label", length = 64)
    private String versionLabel;

    @Column(name = "app_package_id")
    private Long appPackageId;

    @Column(name = "suite_id")
    private Long suiteId;

    @Column(name = "env_id")
    private Long envId;

    @Column(name = "config_json", columnDefinition = "JSON")
    private String configJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BaselineStatus status = BaselineStatus.active;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum BaselineStatus { active, archived }
}
