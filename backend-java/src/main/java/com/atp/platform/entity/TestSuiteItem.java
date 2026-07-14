package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "test_suite_items")
public class TestSuiteItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "suite_id", nullable = false)
    private Long suiteId;
    @Column(name = "case_id", nullable = false)
    private Long caseId;
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    private Boolean enabled = true;
}
