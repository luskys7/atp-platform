package com.atp.platform.repository;

import com.atp.platform.entity.AssertPolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssertPolicyRuleRepository extends JpaRepository<AssertPolicyRule, Long> {
    List<AssertPolicyRule> findByEnabledTrueOrderByRuleTypeAscTargetTypeAsc();
}
