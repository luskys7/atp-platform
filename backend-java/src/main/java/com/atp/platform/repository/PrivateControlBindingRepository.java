package com.atp.platform.repository;

import com.atp.platform.entity.PrivateControlBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivateControlBindingRepository extends JpaRepository<PrivateControlBinding, Long> {
    List<PrivateControlBinding> findByTaskIdOrderByStepIndexAsc(Long taskId);

    List<PrivateControlBinding> findByPoolId(Long poolId);

    Optional<PrivateControlBinding> findByTaskIdAndStepIndexAndElementNameAndIsIsolated(
            Long taskId, Integer stepIndex, String elementName, Boolean isIsolated);

    void deleteByTaskId(Long taskId);
}
