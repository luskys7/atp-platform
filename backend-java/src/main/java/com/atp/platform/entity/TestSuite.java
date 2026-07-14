package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "test_suites")
public class TestSuite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 256)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(length = 512)
    private String tags;
    @Column(name = "env_id")
    private Long envId;
    @Enumerated(EnumType.STRING)
    @Column(name = "exec_mode", nullable = false, length = 16)
    private ExecMode execMode = ExecMode.serial;
    @Enumerated(EnumType.STRING)
    @Column(name = "fail_policy", nullable = false, length = 16)
    private FailPolicy failPolicy = FailPolicy.continue_on_fail;
    @Column(name = "hook_before", columnDefinition = "TEXT")
    private String hookBefore;
    @Column(name = "hook_after", columnDefinition = "TEXT")
    private String hookAfter;
    @Column(name = "created_by")
    private Long createdBy;
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

    public enum ExecMode { serial, parallel }
    public enum FailPolicy { continue_on_fail, stop }
}
