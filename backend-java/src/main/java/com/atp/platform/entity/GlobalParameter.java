package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "global_parameters", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"scope", "env_id", "param_key"})
})
public class GlobalParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParamScope scope = ParamScope.platform;

    @Column(name = "env_id")
    private Long envId;

    @Column(name = "param_key", nullable = false, length = 128)
    private String paramKey;

    @Column(name = "param_value", columnDefinition = "TEXT")
    private String paramValue;

    @Column(name = "value_cipher", columnDefinition = "TEXT")
    private String valueCipher;

    @Column(name = "is_sensitive")
    private Boolean sensitive = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "version_num")
    private Integer versionNum = 1;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ParamScope { platform, env }
}
