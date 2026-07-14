package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "data_set_rows")
public class DataSetRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;
    @Column(name = "row_data_json", columnDefinition = "JSON", nullable = false)
    private String rowDataJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "lock_status", nullable = false, length = 16)
    private LockStatus lockStatus = LockStatus.idle;
    @Column(name = "locked_by_task_id")
    private Long lockedByTaskId;

    public enum LockStatus { idle, busy }
}
