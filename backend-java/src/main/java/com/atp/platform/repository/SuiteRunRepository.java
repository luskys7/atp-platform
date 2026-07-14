package com.atp.platform.repository;

import com.atp.platform.entity.SuiteRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuiteRunRepository extends JpaRepository<SuiteRun, Long> {
    List<SuiteRun> findBySuiteIdOrderByStartedAtDesc(Long suiteId);
}
