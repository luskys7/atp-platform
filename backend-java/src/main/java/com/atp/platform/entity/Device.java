package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 128)
    private String serialNumber;

    @Column(length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Platform platform;

    @Column(name = "os_version", length = 32)
    private String osVersion;

    @Column(length = 128)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceStatus status = DeviceStatus.offline;

    @Column(name = "agent_host", length = 128)
    private String agentHost;

    @Column(name = "agent_port")
    private Integer agentPort;

    /**
     * 该设备所属执行器 HTTP 基址，如 http://10.0.0.12:9002。
     * 中央后端按此字段路由控机/投屏/任务；为空则回退到 atp.executor.url。
     */
    @Column(name = "executor_url", length = 256)
    private String executorUrl;

    @Column(name = "battery_level")
    private Integer batteryLevel = 0;

    @Column(name = "screen_width")
    private Integer screenWidth;

    @Column(name = "screen_height")
    private Integer screenHeight;

    @Column(name = "wda_port")
    private Integer wdaPort;

    @Column(name = "adb_port")
    private Integer adbPort = 5037;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    @Column(name = "locked_by_task_id")
    private Long lockedByTaskId;

    @Column(name = "lock_expires_at")
    private LocalDateTime lockExpiresAt;

    @Column(name = "is_whitelisted")
    private Boolean isWhitelisted = false;

    /** 手势坐标校准 JSON: {"offset_x":0,"offset_y":0,"scale_x":1.0,"scale_y":1.0} */
    @Column(name = "calibration_json", columnDefinition = "JSON")
    private String calibrationJson;

    /** 设备标签，逗号分隔，用于任务筛选 */
    @Column(length = 512)
    private String tags;

    @Column(name = "fail_count")
    private Integer failCount = 0;

    @Column(name = "team_id")
    private Long teamId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Platform {
        android, ios
    }

    public enum DeviceStatus {
        online, offline, busy, maintenance, error
    }
}
