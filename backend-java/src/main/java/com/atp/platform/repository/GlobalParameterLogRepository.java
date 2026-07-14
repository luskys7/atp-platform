package com.atp.platform.repository;

import com.atp.platform.entity.GlobalParameterLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GlobalParameterLogRepository extends JpaRepository<GlobalParameterLog, Long> {
    List<GlobalParameterLog> findByParamIdOrderByCreatedAtDesc(Long paramId);
}
