package com.atp.platform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "test_reports")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TestReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", unique = true)
    private Long taskId;

    @Column(length = 256)
    private String title;

    @Column(name = "total_executions")
    private Integer totalExecutions = 0;

    @Column(name = "success_count")
    private Integer successCount = 0;

    @Column(name = "failed_count")
    private Integer failedCount = 0;

    @Column(name = "pass_rate")
    private Double passRate = 0.0;

    @Column(name = "defect_id", length = 64)
    private String defectId;

    @Column(name = "perf_metrics_json", columnDefinition = "TEXT")
    private String perfMetricsJson;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "pdf_path", length = 512)
    private String pdfPath;

    @Column(name = "module_name", length = 128)
    private String moduleName;

    @Column(name = "version_label", length = 64)
    private String versionLabel;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    @Column(name = "operator_label", length = 128)
    private String operatorLabel;

    @Column(name = "device_label", length = 256)
    private String deviceLabel;

    /** 归档后永久留存，不参与自动清理 */
    @Column(name = "archived")
    private Boolean archived = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", insertable = false, updatable = false)
    @JsonIgnore
    private TestTask task;
}
