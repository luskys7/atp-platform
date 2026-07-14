package com.atp.platform.repository;

import com.atp.platform.entity.LocatorFailureRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LocatorFailureRecordRepository extends JpaRepository<LocatorFailureRecord, Long> {

    List<LocatorFailureRecord> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    @Query("""
            SELECT r.failureClass, COUNT(r) FROM LocatorFailureRecord r
            WHERE r.createdAt >= :since GROUP BY r.failureClass
            """)
    List<Object[]> countByFailureClassSince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT r.elementName, COUNT(r) FROM LocatorFailureRecord r
            WHERE r.createdAt >= :since GROUP BY r.elementName ORDER BY COUNT(r) DESC
            """)
    List<Object[]> topFailedElementsSince(@Param("since") LocalDateTime since, org.springframework.data.domain.Pageable pageable);
}
