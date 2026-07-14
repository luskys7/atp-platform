package com.atp.platform.repository;

import com.atp.platform.entity.VersionBaseline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersionBaselineRepository extends JpaRepository<VersionBaseline, Long> {
    List<VersionBaseline> findByStatusOrderByUpdatedAtDesc(VersionBaseline.BaselineStatus status);
}
