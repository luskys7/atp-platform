package com.atp.platform.repository;

import com.atp.platform.entity.CiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CiConfigRepository extends JpaRepository<CiConfig, Long> {
    Optional<CiConfig> findFirstByOrderByIdAsc();
}
