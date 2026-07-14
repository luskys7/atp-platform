package com.atp.platform.repository;

import com.atp.platform.entity.OperationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationRecordRepository extends JpaRepository<OperationRecord, Long> {

    Page<OperationRecord> findByStatusOrderByCreatedAtDesc(OperationRecord.RecordStatus status, Pageable pageable);

    Page<OperationRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
