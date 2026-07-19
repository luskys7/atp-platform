package com.atp.platform.repository;

import com.atp.platform.entity.TestReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TestReportRepository extends JpaRepository<TestReport, Long>,
        JpaSpecificationExecutor<TestReport> {
    Optional<TestReport> findByTaskId(Long taskId);
    List<TestReport> findTop10ByOrderByCreatedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TestReport r WHERE r.taskId = :taskId")
    int deleteAllByTaskId(@Param("taskId") Long taskId);

    void deleteByTaskId(Long taskId);

    @Query("SELECT DISTINCT r.moduleName FROM TestReport r WHERE r.moduleName IS NOT NULL AND r.moduleName <> '' ORDER BY r.moduleName")
    List<String> distinctModules();

    @Query("SELECT DISTINCT r.versionLabel FROM TestReport r WHERE r.versionLabel IS NOT NULL AND r.versionLabel <> '' ORDER BY r.versionLabel")
    List<String> distinctVersions();
}
