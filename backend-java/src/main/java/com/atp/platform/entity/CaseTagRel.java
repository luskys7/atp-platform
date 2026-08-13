package com.atp.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用例-功能标签绑定。
 * 对应 PRD：case_tag_rel
 */
@Data
@Entity
@Table(name = "case_tag_rel", uniqueConstraints = {
        @UniqueConstraint(name = "uk_case_tag", columnNames = {"case_id", "tag_id"})
})
public class CaseTagRel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
