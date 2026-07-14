package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "test_cases")
public class TestCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "folder_id")
    private Long folderId;
    @Column(nullable = false, length = 256)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String preconditions;
    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;
    @Column(name = "steps_content", columnDefinition = "LONGTEXT")
    private String stepsContent;
    @Column(name = "script_type", length = 16)
    private String scriptType = "visual";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Platform platform = Platform.android;
    @Column(name = "app_package", length = 256)
    private String appPackage;
    private Byte priority = 3;
    @Column(length = 512)
    private String tags;
    @Enumerated(EnumType.STRING)
    @Column(name = "case_status", nullable = false, length = 16)
    private CaseStatus caseStatus = CaseStatus.draft;
    @Column(name = "version_num")
    private Integer versionNum = 1;
    @Column(name = "owner_id")
    private Long ownerId;
    @Column(name = "requirement_id", length = 64)
    private String requirementId;
    @Column(name = "defect_id", length = 64)
    private String defectId;
    @Column(name = "env_id")
    private Long envId;
    @Column(name = "dataset_id")
    private Long datasetId;
    @Column(name = "enable_recording")
    private Boolean enableRecording = true;
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 3600;
    @Column(name = "team_id")
    private Long teamId;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Platform { android, ios, both }
    public enum CaseStatus { draft, review, active, deprecated }
}
