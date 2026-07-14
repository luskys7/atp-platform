package com.atp.platform.service;

import com.atp.platform.entity.AssertPolicyRule;
import com.atp.platform.repository.AssertPolicyRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssertPolicyService {

    private final AssertPolicyRuleRepository repository;
    private final ObjectMapper objectMapper;

    public List<AssertPolicyRule> list() {
        return repository.findAll();
    }

    public List<AssertPolicyRule> enabledRules() {
        return repository.findByEnabledTrueOrderByRuleTypeAscTargetTypeAsc();
    }

    @Transactional
    public AssertPolicyRule create(Map<String, Object> body, Long userId) {
        AssertPolicyRule r = map(new AssertPolicyRule(), body);
        r.setCreatedBy(userId);
        return repository.save(r);
    }

    @Transactional
    public AssertPolicyRule update(Long id, Map<String, Object> body) {
        AssertPolicyRule r = repository.findById(id).orElseThrow();
        return repository.save(map(r, body));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** 供执行器注入：{ whitelist: [...], blacklist: [...] } */
    public String buildPolicyJson() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode wl = root.putArray("whitelist");
        ArrayNode bl = root.putArray("blacklist");
        for (AssertPolicyRule r : enabledRules()) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("target_type", r.getTargetType().name());
            item.put("pattern", r.getPattern());
            if (r.getRuleType() == AssertPolicyRule.RuleType.whitelist) {
                wl.add(item);
            } else {
                bl.add(item);
            }
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"whitelist\":[],\"blacklist\":[]}";
        }
    }

    private AssertPolicyRule map(AssertPolicyRule r, Map<String, Object> body) {
        if (body.containsKey("rule_type")) {
            r.setRuleType(AssertPolicyRule.RuleType.valueOf(body.get("rule_type").toString()));
        }
        if (body.containsKey("target_type")) {
            r.setTargetType(AssertPolicyRule.TargetType.valueOf(body.get("target_type").toString()));
        }
        if (body.containsKey("pattern")) r.setPattern(body.get("pattern").toString());
        if (body.containsKey("description")) r.setDescription(str(body.get("description")));
        if (body.containsKey("enabled") && body.get("enabled") != null) {
            r.setEnabled(Boolean.parseBoolean(body.get("enabled").toString()));
        }
        return r;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
