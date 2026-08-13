package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 功能标签（机型适配筛选）。
 * 层级：功能集(tag_type) → 一级板块(tag_name) → 板块内容(content_name)
 */
@Data
@Entity
@Table(name = "function_tag", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feature_board_content_team",
                columnNames = {"tag_type", "tag_name", "content_name", "team_id"})
})
public class FunctionTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 一级板块，如：清洁、通知 */
    @Column(name = "tag_name", nullable = false, length = 128)
    private String tagName;

    /** 功能集，如：清洁功能、通知体系 */
    @Column(name = "tag_type", length = 64)
    private String tagType;

    /** 板块内容，如：清扫、任务完成通知 */
    @Column(name = "content_name", nullable = false, length = 128)
    private String contentName = "";

    /** 1-启用 0-停用 */
    @Column(nullable = false)
    private Byte status = 1;

    @Column(name = "team_id")
    private Long teamId;

    @Column(length = 512)
    private String description;

    /**
     * 绑定的公共步骤 ID；勾选功能拼装用例时按序插入 invoke_common。
     * 为空表示该功能项暂不可参与自动组合。
     */
    @Column(name = "common_step_id")
    private Long commonStepId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
