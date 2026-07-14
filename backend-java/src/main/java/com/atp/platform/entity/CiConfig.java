package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ci_config")
public class CiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_token", nullable = false, length = 128)
    private String webhookToken;

    private Boolean enabled = true;

    @Column(name = "default_platform", length = 16)
    private String defaultPlatform = "android";

    @Column(name = "default_script_type", length = 16)
    private String defaultScriptType = "python";

    @Column(name = "default_script_content", columnDefinition = "TEXT")
    private String defaultScriptContent = "print('CI triggered task')";

    @Column(name = "default_app_package", length = 256)
    private String defaultAppPackage;

    @Column(name = "default_timeout_seconds")
    private Integer defaultTimeoutSeconds = 3600;

    @Column(name = "auto_submit")
    private Boolean autoSubmit = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
