package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "app_packages")
public class AppPackage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 256)
    private String name;
    @Column(name = "package_name", length = 256)
    private String packageName;
    @Column(name = "version_name", length = 64)
    private String versionName;
    @Column(name = "version_code", length = 32)
    private String versionCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Platform platform = Platform.android;
    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;
    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;
    @Column(name = "file_size")
    private Long fileSize = 0L;
    @Column(name = "md5_hash", nullable = false, length = 64)
    private String md5Hash;
    @Column(length = 128)
    private String branch;
    @Column(name = "build_log", columnDefinition = "TEXT")
    private String buildLog;
    @Enumerated(EnumType.STRING)
    @Column(name = "package_channel", length = 16)
    private PackageChannel packageChannel = PackageChannel.test;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PackageStatus status = PackageStatus.active;
    @Column(name = "uploaded_by")
    private Long uploadedBy;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Platform { android, ios }
    public enum PackageStatus { active, deprecated }

    public enum PackageChannel { test, internal, beta, production }
}
