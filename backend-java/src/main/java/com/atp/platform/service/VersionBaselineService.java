package com.atp.platform.service;

import com.atp.platform.entity.*;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VersionBaselineService {

    private final VersionBaselineRepository baselineRepository;
    private final TestSuiteRepository suiteRepository;
    private final TestSuiteItemRepository suiteItemRepository;
    private final AppPackageRepository appPackageRepository;
    private final TestEnvironmentRepository envRepository;
    private final ObjectMapper objectMapper;

    public List<VersionBaseline> list() {
        return baselineRepository.findByStatusOrderByUpdatedAtDesc(VersionBaseline.BaselineStatus.active);
    }

    public VersionBaseline get(Long id) {
        return baselineRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "版本基线不存在", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public VersionBaseline create(Map<String, Object> body, Long userId) {
        VersionBaseline b = map(new VersionBaseline(), body);
        b.setCreatedBy(userId);
        return baselineRepository.save(b);
    }

    @Transactional
    public VersionBaseline update(Long id, Map<String, Object> body) {
        VersionBaseline b = get(id);
        return baselineRepository.save(map(b, body));
    }

    @Transactional
    public void archive(Long id) {
        VersionBaseline b = get(id);
        b.setStatus(VersionBaseline.BaselineStatus.archived);
        baselineRepository.save(b);
    }

    public Map<String, Object> compareWithCurrent(Long id) {
        VersionBaseline baseline = get(id);
        Map<String, Object> current = new LinkedHashMap<>();
        Map<String, Object> baselineConfig = parseConfig(baseline.getConfigJson());
        List<Map<String, Object>> diffs = new ArrayList<>();

        if (baseline.getAppPackageId() != null) {
            var appOpt = appPackageRepository.findById(baseline.getAppPackageId());
            Map<String, Object> appSnap = appOpt.map(a -> Map.<String, Object>of(
                    "id", a.getId(),
                    "name", a.getName() != null ? a.getName() : "",
                    "package_name", a.getPackageName() != null ? a.getPackageName() : "",
                    "version", a.getVersionName() != null ? a.getVersionName() : ""
            )).orElse(Map.of("missing", true));
            current.put("app_package", appSnap);
            if (appOpt.isEmpty()) {
                diffs.add(Map.of("field", "app_package_id", "expected", baseline.getAppPackageId(), "actual", null));
            }
        }

        if (baseline.getSuiteId() != null) {
            var suiteOpt = suiteRepository.findById(baseline.getSuiteId());
            if (suiteOpt.isPresent()) {
                var suite = suiteOpt.get();
                long itemCount = suiteItemRepository.countBySuiteId(suite.getId());
                current.put("suite", Map.of(
                        "id", suite.getId(),
                        "name", suite.getName() != null ? suite.getName() : "",
                        "item_count", itemCount
                ));
                Object expectedCount = baselineConfig.get("suite_item_count");
                if (expectedCount != null && !expectedCount.toString().equals(String.valueOf(itemCount))) {
                    diffs.add(Map.of("field", "suite_item_count", "expected", expectedCount, "actual", itemCount));
                }
            } else {
                current.put("suite", Map.of("missing", true));
                diffs.add(Map.of("field", "suite_id", "expected", baseline.getSuiteId(), "actual", null));
            }
        }

        if (baseline.getEnvId() != null) {
            var envOpt = envRepository.findById(baseline.getEnvId());
            Map<String, Object> envSnap = envOpt.map(e -> Map.<String, Object>of(
                    "id", e.getId(),
                    "name", e.getName() != null ? e.getName() : "",
                    "base_url", e.getBaseUrl() != null ? e.getBaseUrl() : ""
            )).orElse(Map.of("missing", true));
            current.put("environment", envSnap);
            if (envOpt.isEmpty()) {
                diffs.add(Map.of("field", "env_id", "expected", baseline.getEnvId(), "actual", null));
            }
        }

        for (Map.Entry<String, Object> entry : baselineConfig.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_id") || "suite_item_count".equals(key)) {
                continue;
            }
            Object expected = entry.getValue();
            Object actual = current.get(key);
            if (actual == null && !current.containsKey(key)) {
                diffs.add(Map.of("field", key, "expected", expected, "actual", null, "note", "config only"));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseline_id", baseline.getId());
        result.put("baseline_name", baseline.getName());
        result.put("version_label", baseline.getVersionLabel());
        result.put("baseline_config", baselineConfig);
        result.put("current_snapshot", current);
        result.put("diffs", diffs);
        result.put("has_diff", !diffs.isEmpty());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    private VersionBaseline map(VersionBaseline b, Map<String, Object> body) {
        if (body.containsKey("name")) b.setName(body.get("name").toString());
        if (body.containsKey("description")) b.setDescription(str(body.get("description")));
        if (body.containsKey("version_label")) b.setVersionLabel(str(body.get("version_label")));
        if (body.containsKey("app_package_id") && body.get("app_package_id") != null) {
            b.setAppPackageId(Long.valueOf(body.get("app_package_id").toString()));
        }
        if (body.containsKey("suite_id") && body.get("suite_id") != null) {
            b.setSuiteId(Long.valueOf(body.get("suite_id").toString()));
        }
        if (body.containsKey("env_id") && body.get("env_id") != null) {
            b.setEnvId(Long.valueOf(body.get("env_id").toString()));
        }
        if (body.containsKey("config_json")) b.setConfigJson(str(body.get("config_json")));
        return b;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
