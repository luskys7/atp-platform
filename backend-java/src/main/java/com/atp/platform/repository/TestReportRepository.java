package com.atp.platform.repository;

import com.atp.platform.entity.TestReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TestReportRepository extends JpaRepository<TestReport, Long> {
    Optional<TestReport> findByTaskId(Long taskId);
    List<TestReport> findTop10ByOrderByCreatedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TestReport r WHERE r.taskId = :taskId")
    int deleteAllByTaskId(@Param("taskId") Long taskId);

    void deleteByTaskId(Long taskId);
}
