package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "test_environments")
public class TestEnvironment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 128)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "env_type", nullable = false, length = 16)
    private EnvType envType = EnvType.test;
    @Column(name = "base_url", length = 512)
    private String baseUrl;
    @Column(name = "config_json", columnDefinition = "JSON")
    private String configJson;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnvStatus status = EnvStatus.active;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum EnvType { test, staging, gray, prod }
    public enum EnvStatus { active, disabled }
}
