package com.atp.platform.repository;

import com.atp.platform.entity.TestSuite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {
    List<TestSuite> findByDeletedAtIsNullOrderByUpdatedAtDesc();
    List<TestSuite> findByDeletedAtIsNullAndTeamIdOrderByUpdatedAtDesc(Long teamId);
}
