package com.atp.platform.repository;

import com.atp.platform.entity.SuiteRunItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuiteRunItemRepository extends JpaRepository<SuiteRunItem, Long> {
    List<SuiteRunItem> findByRunIdOrderBySortOrderAsc(Long runId);
    Optional<SuiteRunItem> findByTaskId(Long taskId);
    Optional<SuiteRunItem> findFirstByRunIdAndStatusOrderBySortOrderAsc(Long runId, SuiteRunItem.ItemStatus status);
    boolean existsByRunIdAndStatus(Long runId, SuiteRunItem.ItemStatus status);
}
