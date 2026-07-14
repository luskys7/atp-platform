package com.atp.platform.repository;

import com.atp.platform.entity.ExecutionStepMarker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExecutionStepMarkerRepository extends JpaRepository<ExecutionStepMarker, Long> {

    List<ExecutionStepMarker> findByTaskIdOrderByStepIndexAscVideoOffsetMsAsc(Long taskId);

    List<ExecutionStepMarker> findByTaskIdAndExecutionIdOrderByStepIndexAsc(Long taskId, Long executionId);

    Optional<ExecutionStepMarker> findFirstByTaskIdAndStepIndexOrderByCreatedAtDesc(Long taskId, Integer stepIndex);

    void deleteByTaskId(Long taskId);
}
