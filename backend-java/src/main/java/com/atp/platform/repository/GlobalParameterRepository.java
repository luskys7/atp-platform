package com.atp.platform.repository;

import com.atp.platform.entity.GlobalParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GlobalParameterRepository extends JpaRepository<GlobalParameter, Long> {
    List<GlobalParameter> findByScopeAndEnabledTrueOrderByParamKeyAsc(GlobalParameter.ParamScope scope);
    List<GlobalParameter> findByScopeAndEnvIdAndEnabledTrueOrderByParamKeyAsc(GlobalParameter.ParamScope scope, Long envId);
    List<GlobalParameter> findAllByOrderByScopeAscParamKeyAsc();
    Optional<GlobalParameter> findByScopeAndEnvIdAndParamKey(GlobalParameter.ParamScope scope, Long envId, String paramKey);
}
