package com.atp.platform.repository;

import com.atp.platform.entity.CiJobRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CiJobRecordRepository extends JpaRepository<CiJobRecord, Long> {
    Optional<CiJobRecord> findByTaskId(Long taskId);
    List<CiJobRecord> findTop20ByOrderByCreatedAtDesc();
    void deleteByTaskId(Long taskId);
}
