package com.atp.platform.repository;

import com.atp.platform.entity.CaseTagRel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CaseTagRelRepository extends JpaRepository<CaseTagRel, Long> {
    List<CaseTagRel> findByCaseId(Long caseId);

    List<CaseTagRel> findByCaseIdIn(Collection<Long> caseIds);

    List<CaseTagRel> findByTagId(Long tagId);

    List<CaseTagRel> findByTagIdIn(Collection<Long> tagIds);

    Optional<CaseTagRel> findByCaseIdAndTagId(Long caseId, Long tagId);

    void deleteByCaseId(Long caseId);

    void deleteByCaseIdAndTagId(Long caseId, Long tagId);

    void deleteByTagId(Long tagId);

    @Query("SELECT DISTINCT r.caseId FROM CaseTagRel r WHERE r.tagId IN :tagIds")
    List<Long> findDistinctCaseIdsByTagIdIn(@Param("tagIds") Collection<Long> tagIds);
}
