package com.atp.platform.repository;

import com.atp.platform.entity.TaskExecution;
import com.atp.platform.entity.TestTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
    List<TaskExecution> findByTaskId(Long taskId);
    long countByTaskId(Long taskId);
    long countByTaskIdAndStatus(Long taskId, TestTask.TaskStatus status);
    List<TaskExecution> findByTaskIdAndStatus(Long taskId, TestTask.TaskStatus status);
    void deleteByTaskId(Long taskId);
}
