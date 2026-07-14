package com.atp.platform.repository;

import com.atp.platform.entity.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {
    List<ExecutionLog> findByTaskIdOrderByCreatedAtAsc(Long taskId);
    List<ExecutionLog> findByTaskIdAndLogTypeOrderByCreatedAtAsc(Long taskId, String logType);
    List<ExecutionLog> findByTaskIdAndMessageContainingOrderByCreatedAtAsc(Long taskId, String keyword);
    void deleteByTaskId(Long taskId);
}
