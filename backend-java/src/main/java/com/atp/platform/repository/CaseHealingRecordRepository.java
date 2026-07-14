package com.atp.platform.repository;

import com.atp.platform.entity.CaseHealingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseHealingRecordRepository extends JpaRepository<CaseHealingRecord, Long> {
    List<CaseHealingRecord> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    void deleteByTaskId(Long taskId);
}
