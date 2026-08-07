package com.atp.platform.repository;

import com.atp.platform.entity.CiJobRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CiJobRecordRepository extends JpaRepository<CiJobRecord, Long> {
    Optional<CiJobRecord> findByTaskId(Long taskId);
    List<CiJobRecord> findTop20ByOrderByCreatedAtDesc();
    List<CiJobRecord> findAllByOrderByCreatedAtDesc();
    long countByStatusIn(Collection<String> statuses);
    void deleteByTaskId(Long taskId);
}
