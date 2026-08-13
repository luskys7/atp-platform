package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 机型-功能支持关系。
 * 对应 PRD：machine_tag_rel
 */
@Data
@Entity
@Table(name = "machine_tag_rel", uniqueConstraints = {
        @UniqueConstraint(name = "uk_machine_tag", columnNames = {"machine_id", "tag_id"})
})
public class MachineTagRel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false)
    private Long machineId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    /** 1-该机型支持该功能 0-不支持 */
    @Column(name = "is_support", nullable = false)
    private Byte isSupport = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
