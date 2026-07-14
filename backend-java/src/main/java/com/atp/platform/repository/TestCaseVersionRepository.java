package com.atp.platform.repository;

import com.atp.platform.entity.TestCaseVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseVersionRepository extends JpaRepository<TestCaseVersion, Long> {
    List<TestCaseVersion> findByCaseIdOrderByVersionNumDesc(Long caseId);
}
