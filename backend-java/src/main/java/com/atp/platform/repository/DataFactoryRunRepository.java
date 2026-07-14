package com.atp.platform.repository;

import com.atp.platform.entity.DataFactoryRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataFactoryRunRepository extends JpaRepository<DataFactoryRun, Long> {
    List<DataFactoryRun> findByTaskIdAndCleanupStatus(Long taskId, DataFactoryRun.CleanupStatus status);
    List<DataFactoryRun> findBySuiteRunIdAndCleanupStatus(Long suiteRunId, DataFactoryRun.CleanupStatus status);
    void deleteByTaskId(Long taskId);
}
