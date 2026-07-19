package com.atp.platform.repository;

import com.atp.platform.entity.ControlChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ControlChangeLogRepository extends JpaRepository<ControlChangeLog, Long> {
    Page<ControlChangeLog> findByPoolIdOrderByCreatedAtDesc(Long poolId, Pageable pageable);

    void deleteByPoolId(Long poolId);

    @Query("""
            SELECT c.poolId, COUNT(c) FROM ControlChangeLog c
            WHERE c.poolId IS NOT NULL AND c.createdAt >= :since
            GROUP BY c.poolId ORDER BY COUNT(c) DESC
            """)
    List<Object[]> countChangesByPoolSince(@Param("since") LocalDateTime since, Pageable pageable);
}

