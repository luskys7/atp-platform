package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 机型档案（机型适配筛选）。
 * 对应 PRD：machine_info
 */
@Data
@Entity
@Table(name = "machine_info", uniqueConstraints = {
        @UniqueConstraint(name = "uk_machine_name_team", columnNames = {"machine_name", "team_id"})
})
public class MachineInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_name", nullable = false, length = 128)
    private String machineName;

    @Column(name = "hard_version", length = 64)
    private String hardVersion;

    @Column(name = "firm_version", length = 64)
    private String firmVersion;

    /** 1-启用 0-停用 */
    @Column(nullable = false)
    private Byte status = 1;

    @Column(name = "team_id")
    private Long teamId;

    @Column(length = 512)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
