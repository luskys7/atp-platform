package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_user_sessions_user", columnList = "user_id"),
        @Index(name = "idx_user_sessions_jti", columnList = "jti", unique = true)
})
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String jti;

    @Column(name = "device_label", length = 128)
    private String deviceLabel;

    @Column(length = 128)
    private String browser;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "login_at", updatable = false)
    private LocalDateTime loginAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
