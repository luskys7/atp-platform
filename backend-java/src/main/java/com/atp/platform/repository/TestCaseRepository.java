package com.atp.platform.repository;

import com.atp.platform.entity.TestCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    @Query("SELECT c FROM TestCase c WHERE c.deletedAt IS NULL AND (:folderId IS NULL OR c.folderId = :folderId) AND (:status IS NULL OR c.caseStatus = :status) AND (:keyword IS NULL OR c.name LIKE CONCAT('%', :keyword, '%')) AND (:teamId IS NULL OR c.teamId = :teamId)")
    Page<TestCase> findActive(@Param("folderId") Long folderId, @Param("status") TestCase.CaseStatus status, @Param("keyword") String keyword, @Param("teamId") Long teamId, Pageable pageable);

    long countByDeletedAtIsNull();

    List<TestCase> findByDeletedAtIsNull();

    long countByCaseStatusAndDeletedAtIsNull(TestCase.CaseStatus caseStatus);

    long countByFolderIdIsNullAndCaseStatusAndDeletedAtIsNull(TestCase.CaseStatus caseStatus);

    long countByFolderIdAndCaseStatusAndDeletedAtIsNull(Long folderId, TestCase.CaseStatus caseStatus);

    @Query("SELECT COUNT(c) FROM TestCase c WHERE c.folderId IS NULL AND c.deletedAt IS NULL")
    long countActiveByFolderIdIsNull();

    @Query("SELECT COUNT(c) FROM TestCase c WHERE c.folderId = :folderId AND c.deletedAt IS NULL")
    long countActiveByFolderId(@Param("folderId") Long folderId);
}
