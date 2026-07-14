package com.atp.platform.repository;

import com.atp.platform.entity.CommonStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommonStepRepository extends JpaRepository<CommonStep, Long> {
    List<CommonStep> findByDeletedAtIsNullOrderByNameAsc();
    Optional<CommonStep> findByNameAndDeletedAtIsNull(String name);

    @Query("SELECT c FROM CommonStep c WHERE c.deletedAt IS NULL AND c.stepsContent LIKE CONCAT('%', :ref, '%')")
    List<CommonStep> findByStepsContentContaining(@Param("ref") String ref);
}
