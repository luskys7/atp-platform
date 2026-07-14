package com.atp.platform.repository;

import com.atp.platform.entity.TestSuiteItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestSuiteItemRepository extends JpaRepository<TestSuiteItem, Long> {
    List<TestSuiteItem> findBySuiteIdOrderBySortOrderAsc(Long suiteId);
    void deleteBySuiteId(Long suiteId);
    long countByCaseId(Long caseId);
    long countBySuiteId(Long suiteId);
}
