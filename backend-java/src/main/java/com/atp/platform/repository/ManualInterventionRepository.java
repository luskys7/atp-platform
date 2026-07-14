package com.atp.platform.repository;

import com.atp.platform.entity.ManualIntervention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManualInterventionRepository extends JpaRepository<ManualIntervention, Long> {
    List<ManualIntervention> findByStatusOrderByCreatedAtDesc(ManualIntervention.InterventionStatus status);
    Optional<ManualIntervention> findFirstByTaskIdAndStatusOrderByCreatedAtDesc(Long taskId, ManualIntervention.InterventionStatus status);
    List<ManualIntervention> findByTaskIdOrderByCreatedAtDesc(Long taskId);
    void deleteByTaskId(Long taskId);
}
