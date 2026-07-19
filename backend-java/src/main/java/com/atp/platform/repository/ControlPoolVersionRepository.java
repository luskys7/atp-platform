package com.atp.platform.repository;

import com.atp.platform.entity.ControlPoolVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ControlPoolVersionRepository extends JpaRepository<ControlPoolVersion, Long> {
    List<ControlPoolVersion> findByPoolIdOrderByVersionNumDesc(Long poolId);
    Optional<ControlPoolVersion> findByPoolIdAndVersionNum(Long poolId, Integer versionNum);
    Optional<ControlPoolVersion> findTopByPoolIdOrderByVersionNumDesc(Long poolId);

    void deleteByPoolId(Long poolId);
}
