package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "test_accounts")
public class TestAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 128)
    private String username;
    @Column(name = "password_cipher", length = 512)
    private String passwordCipher;
    @Column(length = 32)
    private String phone;
    @Column(length = 512)
    private String tags;
    @Column(name = "env_id")
    private Long envId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status = AccountStatus.active;
    @Column(name = "locked_by_task_id")
    private Long lockedByTaskId;
    @Column(columnDefinition = "TEXT")
    private String remark;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum AccountStatus { active, locked, archived }
}
