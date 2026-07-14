package com.atp.platform.repository;

import com.atp.platform.entity.CaseFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseFolderRepository extends JpaRepository<CaseFolder, Long> {
    List<CaseFolder> findByTeamId(Long teamId);
    List<CaseFolder> findByParentIdOrderBySortOrderAsc(Long parentId);
    List<CaseFolder> findByParentIdIsNullOrderBySortOrderAsc();
    long countByParentId(Long parentId);
}
