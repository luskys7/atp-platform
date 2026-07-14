package com.atp.platform.repository;

import com.atp.platform.entity.TestEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestEnvironmentRepository extends JpaRepository<TestEnvironment, Long> {
    List<TestEnvironment> findByStatusOrderByNameAsc(TestEnvironment.EnvStatus status);
}
