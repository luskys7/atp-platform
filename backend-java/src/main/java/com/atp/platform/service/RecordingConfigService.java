package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.GlobalParameter;
import com.atp.platform.repository.GlobalParameterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 录屏运行时配置：优先读取 platform 全局参数，回退 application.yml 默认值。
 */
@Service
@RequiredArgsConstructor
public class RecordingConfigService {

    private static final String KEY_V2 = "recording.v2_enabled";
    private static final String KEY_MIN_RECOGNITION = "recording.min_recognition_rate";
    private static final String KEY_MIN_LOCATOR = "recording.min_locator_hit_rate";
    private static final String KEY_MAX_LONG_TASKS = "recording.max_long_tasks_per_min";

    private final AtpProperties properties;
    private final GlobalParameterRepository globalParameterRepository;

    public Map<String, Object> getFeatures() {
        AtpProperties.Recording defaults = properties.getRecording();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recording_v2", readBool(KEY_V2, defaults.isV2Enabled()));
        out.put("min_recognition_rate", readDouble(KEY_MIN_RECOGNITION, defaults.getMinRecognitionRate()));
        out.put("min_locator_hit_rate", readDouble(KEY_MIN_LOCATOR, defaults.getMinLocatorHitRate()));
        out.put("max_long_tasks_per_min", readInt(KEY_MAX_LONG_TASKS, defaults.getMaxLongTasksPerMin()));
        out.put("runtime_overrides", hasRuntimeOverrides());
        out.put("source", hasRuntimeOverrides() ? "runtime" : "yaml_default");
        return out;
    }

    @Transactional
    public Map<String, Object> updateFeatures(Map<String, Object> body, Long operatorId) {
        if (body.containsKey("recording_v2")) {
            upsert(KEY_V2, String.valueOf(boolVal(body.get("recording_v2"))),
                    "录屏 v2 功能开关", operatorId);
        }
        if (body.containsKey("min_recognition_rate")) {
            upsert(KEY_MIN_RECOGNITION, String.valueOf(doubleVal(body.get("min_recognition_rate"))),
                    "录屏识别率审计阈值(%)", operatorId);
        }
        if (body.containsKey("min_locator_hit_rate")) {
            upsert(KEY_MIN_LOCATOR, String.valueOf(doubleVal(body.get("min_locator_hit_rate"))),
                    "录屏定位命中率阈值(%)", operatorId);
        }
        if (body.containsKey("max_long_tasks_per_min")) {
            upsert(KEY_MAX_LONG_TASKS, String.valueOf(intVal(body.get("max_long_tasks_per_min"))),
                    "CPU 长任务每分钟上限", operatorId);
        }
        return getFeatures();
    }

    @Transactional
    public Map<String, Object> resetToYamlDefaults(Long operatorId) {
        deleteIfExists(KEY_V2);
        deleteIfExists(KEY_MIN_RECOGNITION);
        deleteIfExists(KEY_MIN_LOCATOR);
        deleteIfExists(KEY_MAX_LONG_TASKS);
        return getFeatures();
    }

    public double getMinRecognitionRate() {
        return readDouble(KEY_MIN_RECOGNITION, properties.getRecording().getMinRecognitionRate());
    }

    public double getMinLocatorHitRate() {
        return readDouble(KEY_MIN_LOCATOR, properties.getRecording().getMinLocatorHitRate());
    }

    public boolean isV2Enabled() {
        return readBool(KEY_V2, properties.getRecording().isV2Enabled());
    }

    private boolean hasRuntimeOverrides() {
        return findPlatform(KEY_V2).isPresent()
                || findPlatform(KEY_MIN_RECOGNITION).isPresent()
                || findPlatform(KEY_MIN_LOCATOR).isPresent()
                || findPlatform(KEY_MAX_LONG_TASKS).isPresent();
    }

    private void upsert(String key, String value, String description, Long operatorId) {
        GlobalParameter p = findPlatform(key).orElseGet(GlobalParameter::new);
        if (p.getId() == null) {
            p.setScope(GlobalParameter.ParamScope.platform);
            p.setEnvId(null);
            p.setParamKey(key);
            p.setCreatedBy(operatorId);
            p.setVersionNum(1);
            p.setEnabled(true);
            p.setSensitive(false);
        } else {
            p.setVersionNum(p.getVersionNum() + 1);
        }
        p.setParamValue(value);
        p.setDescription(description);
        globalParameterRepository.save(p);
    }

    private void deleteIfExists(String key) {
        findPlatform(key).ifPresent(globalParameterRepository::delete);
    }

    private Optional<GlobalParameter> findPlatform(String key) {
        return globalParameterRepository.findByScopeAndEnvIdAndParamKey(
                GlobalParameter.ParamScope.platform, null, key);
    }

    private boolean readBool(String key, boolean defaultVal) {
        return findPlatform(key)
                .filter(GlobalParameter::getEnabled)
                .map(p -> "true".equalsIgnoreCase(p.getParamValue()))
                .orElse(defaultVal);
    }

    private double readDouble(String key, double defaultVal) {
        return findPlatform(key)
                .filter(GlobalParameter::getEnabled)
                .map(p -> {
                    try {
                        return Double.parseDouble(p.getParamValue());
                    } catch (Exception e) {
                        return defaultVal;
                    }
                })
                .orElse(defaultVal);
    }

    private int readInt(String key, int defaultVal) {
        return findPlatform(key)
                .filter(GlobalParameter::getEnabled)
                .map(p -> {
                    try {
                        return Integer.parseInt(p.getParamValue());
                    } catch (Exception e) {
                        return defaultVal;
                    }
                })
                .orElse(defaultVal);
    }

    private boolean boolVal(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(o.toString());
    }

    private double doubleVal(Object o) {
        return o == null ? 0 : Double.parseDouble(o.toString());
    }

    private int intVal(Object o) {
        return o == null ? 0 : Integer.parseInt(o.toString());
    }
}
