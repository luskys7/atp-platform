package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "login_logs", indexes = {
        @Index(name = "idx_login_logs_user_time", columnList = "user_id,login_at")
})
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 64)
    private String ip;

    @Column(name = "device_label", length = 128)
    private String deviceLabel;

    @Column(length = 128)
    private String browser;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** success / failed */
    @Column(length = 16)
    private String status = "success";

    /** 是否异地（相对上次登录 IP） */
    @Column(name = "remote_login")
    private Boolean remoteLogin = false;

    @Column(length = 256)
    private String message;

    @CreationTimestamp
    @Column(name = "login_at", updatable = false)
    private LocalDateTime loginAt;
}
