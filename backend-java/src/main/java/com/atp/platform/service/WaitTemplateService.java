package com.atp.platform.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WaitTemplateService {

    private static final Map<String, Map<String, String>> TEMPLATES = Map.of(
            "smoke", Map.of("WAIT_MULTIPLIER", "0.5", "WAIT_TEMPLATE_LABEL", "冒烟"),
            "standard", Map.of("WAIT_MULTIPLIER", "1.0", "WAIT_TEMPLATE_LABEL", "标准"),
            "weak_network", Map.of(
                    "WAIT_MULTIPLIER", "2.5",
                    "WAIT_TEMPLATE_LABEL", "弱网",
                    "DEFAULT_NETWORK_PROFILE", "high_latency"
            )
    );

    public Map<String, String> resolve(String templateName) {
        if (templateName == null || templateName.isBlank()) return Map.of();
        Map<String, String> tpl = TEMPLATES.get(templateName.toLowerCase().trim());
        return tpl != null ? new LinkedHashMap<>(tpl) : Map.of();
    }

    public Map<String, String> allTemplates() {
        Map<String, String> labels = new LinkedHashMap<>();
        TEMPLATES.forEach((k, v) -> labels.put(k, v.getOrDefault("WAIT_TEMPLATE_LABEL", k)));
        return labels;
    }
}
