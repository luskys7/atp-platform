package com.atp.platform.repository;

import com.atp.platform.entity.TestTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestTaskRepository extends JpaRepository<TestTask, Long> {

    @Query("SELECT t FROM TestTask t WHERE (:status IS NULL OR t.status = :status) " +
           "AND (:createdBy IS NULL OR t.createdBy = :createdBy) " +
           "AND (:teamId IS NULL OR t.teamId = :teamId)")
    Page<TestTask> findByFilters(@Param("status") TestTask.TaskStatus status,
                                 @Param("createdBy") Long createdBy,
                                 @Param("teamId") Long teamId,
                                 Pageable pageable);

    long countByStatus(TestTask.TaskStatus status);

    List<TestTask> findByStatusOrderByStartedAtDesc(TestTask.TaskStatus status);

    List<TestTask> findByStatusInOrderByPriorityAscCreatedAtAsc(List<TestTask.TaskStatus> statuses);

    List<TestTask> findByParentTaskId(Long parentTaskId);
}
