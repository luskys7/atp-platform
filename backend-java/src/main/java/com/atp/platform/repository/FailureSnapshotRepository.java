package com.atp.platform.repository;

import com.atp.platform.entity.FailureSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FailureSnapshotRepository extends JpaRepository<FailureSnapshot, Long> {
    List<FailureSnapshot> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    void deleteByTaskId(Long taskId);
}
