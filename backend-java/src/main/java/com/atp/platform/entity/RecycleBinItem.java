package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "recycle_bin_items")
public class RecycleBinItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;
    @Column(name = "resource_name", nullable = false, length = 256)
    private String resourceName;
    @Column(name = "snapshot_json", columnDefinition = "LONGTEXT")
    private String snapshotJson;
    @Column(name = "deleted_by")
    private Long deletedBy;
    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;
    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;
}
