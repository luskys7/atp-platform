package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "data_factory_templates")
public class DataFactoryTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 16)
    private String method = "POST";

    @Column(name = "url_template", nullable = false, columnDefinition = "TEXT")
    private String urlTemplate;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson = "{}";

    @Column(name = "body_template", columnDefinition = "TEXT")
    private String bodyTemplate;

    /** JSON: {"order_id": "/data/id"} JsonPointer 提取 */
    @Column(name = "extract_json", columnDefinition = "TEXT")
    private String extractJson = "{}";

    @Column(name = "cleanup_method", length = 16)
    private String cleanupMethod = "DELETE";

    @Column(name = "cleanup_url_template", columnDefinition = "TEXT")
    private String cleanupUrlTemplate;

    @Column(name = "cleanup_body_template", columnDefinition = "TEXT")
    private String cleanupBodyTemplate;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
