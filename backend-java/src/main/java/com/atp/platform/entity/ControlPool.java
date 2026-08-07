package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "control_pools")
public class ControlPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pool_key", unique = true, length = 128)
    private String poolKey;

    @Column(name = "app_package", length = 256)
    private String appPackage;

    @Column(name = "page_name", length = 256)
    private String pageName;

    @Column(name = "element_name", length = 256)
    private String elementName;

    @Column(name = "team_id")
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Device.Platform platform;

    @Column(name = "locator_type", length = 16)
    private String locatorType;

    @Column(name = "locator_value", columnDefinition = "TEXT")
    private String locatorValue;

    /** 设备侧原始关联文案/属性值，如 content-desc、text、resource-id 短名（默认/兜底） */
    @Column(name = "device_element_value", length = 512)
    private String deviceElementValue;

    /** 按手机型号映射的控件元素值 JSON：[{"device_model":"LGE-AN00","element_value":"定时"}] */
    @Column(name = "device_element_bindings", columnDefinition = "JSON")
    private String deviceElementBindings;

    @Column(name = "feature_vector", columnDefinition = "JSON")
    private String featureVector;

    @Column(name = "hit_count")
    private Integer hitCount = 0;

    @Column(name = "version_tag", length = 64)
    private String versionTag;

    @Column(name = "env_tag", length = 64)
    private String envTag = "";

    @Column(name = "is_core")
    private Boolean isCore = false;

    @Column(name = "control_tag", length = 32)
    private String controlTag = "static";

    @Column(length = 16)
    private String status = "active";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
