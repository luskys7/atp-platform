package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.common.PageResult;
import com.atp.platform.entity.Device;
import com.atp.platform.entity.OperationRecord;
import com.atp.platform.entity.RecordingResource;
import com.atp.platform.entity.TestCase;
import com.atp.platform.exception.AppException;
import com.atp.platform.util.DesensitizeUtil;
import com.atp.platform.repository.DeviceRepository;
import com.atp.platform.repository.OperationRecordRepository;
import com.atp.platform.repository.RecordingResourceRepository;
import com.atp.platform.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OperationRecordService {

    private final OperationRecordRepository recordRepository;
    private final DeviceRepository deviceRepository;
    private final CaseCatalogService caseCatalogService;
    private final PythonExecutorClient executorClient;
    private final ObjectMapper objectMapper;
    private final RecordingResourceRepository recordingRepository;
    private final AtpProperties properties;
    private final UserRepository userRepository;
    private final RecordingConfigService recordingConfigService;

    public OperationRecord start(Long deviceId, Long userId, Map<String, Object> options) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "设备不存在", HttpStatus.NOT_FOUND));
        String moduleName = strOpt(options, "module_name");
        String versionLabel = strOpt(options, "version_label");
        String projectCode = strOpt(options, "project_code");
        OperationRecord rec = new OperationRecord();
        rec.setDeviceId(deviceId);
        rec.setDeviceSerial(device.getSerialNumber());
        rec.setModuleName(moduleName);
        rec.setVersionLabel(versionLabel);
        rec.setProjectCode(projectCode);
        rec.setStatus(OperationRecord.RecordStatus.recording);
        rec.setCreatedBy(userId);
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("version", 2);
            ObjectNode meta = root.putObject("meta");
            meta.put("started_at", System.currentTimeMillis());
            meta.put("platform", device.getPlatform().name());
            meta.put("device_name", device.getName() != null ? device.getName() : "");
            if (moduleName != null) meta.put("module_name", moduleName);
            if (versionLabel != null) meta.put("version_label", versionLabel);
            if (projectCode != null) meta.put("project_code", projectCode);
            Object taskId = options.get("task_id");
            if (taskId != null && !taskId.toString().isBlank()) {
                meta.put("task_id", longVal(taskId));
            }
            Object startupMs = options.get("startup_ms");
            if (startupMs != null) {
                meta.put("startup_ms", longVal(startupMs));
            }
            Object displayWidth = options.get("display_width");
            if (displayWidth != null && intVal(displayWidth) > 0) {
                meta.put("display_width", intVal(displayWidth));
            }
            Object displayHeight = options.get("display_height");
            if (displayHeight != null && intVal(displayHeight) > 0) {
                meta.put("display_height", intVal(displayHeight));
            }
            meta.putArray("segments");
            meta.put("precondition", "设备已连接，被测应用已安装并可正常访问");
            meta.put("expected_result", "各操作步骤执行成功，页面响应符合预期，无异常弹窗");
            meta.put("environment", device.getPlatform().name());
            meta.putArray("validation_elements");
            meta.putArray("video_annotations");
            root.putArray("steps");
            rec.setStepsJson(objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            rec.setStepsJson("{\"version\":2,\"meta\":{},\"steps\":[]}");
        }
        return recordRepository.save(rec);
    }

    @Transactional
    public OperationRecord appendEvent(Long id, Map<String, Object> event) {
        OperationRecord rec = getWritable(id);
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ArrayNode steps = root.has("steps") ? (ArrayNode) root.get("steps") : root.putArray("steps");
            ObjectNode step = objectMapper.createObjectNode();
            String type = event.getOrDefault("type", "click").toString();
            step.put("type", type);
            step.put("x", intVal(event.get("x")));
            step.put("y", intVal(event.get("y")));
            if (event.containsKey("x2")) step.put("x2", intVal(event.get("x2")));
            if (event.containsKey("y2")) step.put("y2", intVal(event.get("y2")));
            if (event.containsKey("element_name")) step.put("element_name", event.get("element_name").toString());
            if (event.containsKey("display_name")) {
                step.put("display_name", DesensitizeUtil.desensitize(event.get("display_name").toString()));
            }
            if (event.containsKey("text")) {
                String raw = event.get("text").toString();
                if (isProductionRecording(rec)) {
                    step.put("text", DesensitizeUtil.desensitize(raw));
                    step.put("desensitized", true);
                } else {
                    step.put("text", DesensitizeUtil.desensitize(raw));
                    if (DesensitizeUtil.containsSensitive(raw)) {
                        step.put("desensitized", true);
                    }
                }
            }
            if (event.containsKey("key")) step.put("key", event.get("key").toString());
            if (event.containsKey("snapshot_thumb")) {
                step.put("snapshot_thumb", event.get("snapshot_thumb").toString());
            }
            if (event.containsKey("duration_ms")) step.put("duration_ms", intVal(event.get("duration_ms")));
            if (event.containsKey("locator_valid")) step.put("locator_valid", Boolean.parseBoolean(event.get("locator_valid").toString()));
            if (event.containsKey("widget_type")) step.put("widget_type", event.get("widget_type").toString());
            if (event.containsKey("suggested_step_type")) step.put("suggested_step_type", event.get("suggested_step_type").toString());
            if (event.containsKey("locator_type")) step.put("locator_type", event.get("locator_type").toString());
            if (event.containsKey("locator_value")) step.put("locator_value", event.get("locator_value").toString());
            if (event.containsKey("locators")) step.set("locators", objectMapper.valueToTree(event.get("locators")));
            if (event.containsKey("video_offset_ms")) {
                step.put("video_offset_ms", longVal(event.get("video_offset_ms")));
            } else {
                ObjectNode meta = root.has("meta") ? (ObjectNode) root.get("meta") : root.putObject("meta");
                long started = meta.path("started_at").asLong(System.currentTimeMillis());
                step.put("video_offset_ms", Math.max(0, System.currentTimeMillis() - started));
            }
            step.put("enabled", true);
            step.put("timestamp", System.currentTimeMillis());
            steps.add(step);
            rec.setStepsJson(objectMapper.writeValueAsString(root));
            return recordRepository.save(rec);
        } catch (Exception e) {
            throw new AppException("INVALID", "录制事件写入失败", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public OperationRecord patchLastClickStep(Long id, Map<String, Object> patch) {
        OperationRecord rec = getWritable(id);
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ArrayNode steps = root.has("steps") ? (ArrayNode) root.get("steps") : null;
            if (steps == null || steps.isEmpty()) {
                return rec;
            }
            int targetX = patch.containsKey("x") ? intVal(patch.get("x")) : -1;
            int targetY = patch.containsKey("y") ? intVal(patch.get("y")) : -1;
            for (int i = steps.size() - 1; i >= 0; i--) {
                JsonNode step = steps.get(i);
                if (!"click".equals(step.path("type").asText())) {
                    continue;
                }
                if (targetX >= 0 && step.path("x").asInt() != targetX) {
                    continue;
                }
                if (targetY >= 0 && step.path("y").asInt() != targetY) {
                    continue;
                }
                ObjectNode node = (ObjectNode) steps.get(i);
                mergeInspectPatch(node, patch);
                rec.setStepsJson(objectMapper.writeValueAsString(root));
                return recordRepository.save(rec);
            }
            return rec;
        } catch (Exception e) {
            throw new AppException("INVALID", "更新点击定位失败", HttpStatus.BAD_REQUEST);
        }
    }

    private void mergeInspectPatch(ObjectNode step, Map<String, Object> patch) {
        if (patch.containsKey("display_name")) {
            step.put("display_name", DesensitizeUtil.desensitize(patch.get("display_name").toString()));
        }
        if (patch.containsKey("element_name")) {
            step.put("element_name", patch.get("element_name").toString());
        }
        if (patch.containsKey("locator_type")) {
            step.put("locator_type", patch.get("locator_type").toString());
        }
        if (patch.containsKey("locator_value")) {
            step.put("locator_value", patch.get("locator_value").toString());
        }
        if (patch.containsKey("locators")) {
            step.set("locators", objectMapper.valueToTree(patch.get("locators")));
        }
        if (patch.containsKey("widget_type")) {
            step.put("widget_type", patch.get("widget_type").toString());
        }
        if (patch.containsKey("suggested_step_type")) {
            step.put("suggested_step_type", patch.get("suggested_step_type").toString());
        }
        if (patch.containsKey("locator_valid")) {
            step.put("locator_valid", Boolean.parseBoolean(patch.get("locator_valid").toString()));
        }
        if (patch.containsKey("locator_chain")) {
            step.set("locator_chain", objectMapper.valueToTree(patch.get("locator_chain")));
        }
        if (patch.containsKey("risk_level") && patch.get("risk_level") != null) {
            step.put("risk_level", patch.get("risk_level").toString());
        }
        if (patch.containsKey("risk_tags")) {
            step.set("risk_tags", objectMapper.valueToTree(patch.get("risk_tags")));
        }
        if (patch.containsKey("risk_reasons")) {
            step.set("risk_reasons", objectMapper.valueToTree(patch.get("risk_reasons")));
        }
        if (patch.containsKey("validate_result")) {
            step.set("validate_result", objectMapper.valueToTree(patch.get("validate_result")));
        }
        if (patch.containsKey("validated_at") && patch.get("validated_at") != null) {
            step.put("validated_at", patch.get("validated_at").toString());
        }
        if (patch.containsKey("last_validation")) {
            step.set("last_validation", objectMapper.valueToTree(patch.get("last_validation")));
        }
        finalizeStepLocator(step);
    }

    @Transactional
    public OperationRecord patchStepLocator(Long id, int stepIndex, Map<String, Object> patch) {
        OperationRecord rec = get(id);
        if (rec.getStatus() == OperationRecord.RecordStatus.cancelled) {
            throw new AppException("INVALID", "录制已取消", HttpStatus.BAD_REQUEST);
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ArrayNode steps = root.has("steps") ? (ArrayNode) root.get("steps") : null;
            if (steps == null || stepIndex < 0 || stepIndex >= steps.size()) {
                throw new AppException("INVALID", "步骤索引无效", HttpStatus.BAD_REQUEST);
            }
            ObjectNode step = (ObjectNode) steps.get(stepIndex);
            mergeInspectPatch(step, patch);
            if (patch.containsKey("x")) step.put("x", intVal(patch.get("x")));
            if (patch.containsKey("y")) step.put("y", intVal(patch.get("y")));
            rec.setStepsJson(objectMapper.writeValueAsString(root));
            return recordRepository.save(rec);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("INVALID", "更新步骤定位失败", HttpStatus.BAD_REQUEST);
        }
    }

    private void finalizeStepLocator(ObjectNode step) {
        boolean valid = step.path("locator_valid").asBoolean(false);
        if (!valid && step.has("locators") && step.get("locators").isObject() && step.get("locators").size() > 0) {
            valid = true;
            step.put("locator_valid", true);
        }
        if (!valid && !step.path("locator_value").asText("").isBlank()) {
            valid = true;
            step.put("locator_valid", true);
        }
        if (!valid) return;
        step.remove("needs_manual_fix");
        String type = step.path("type").asText("");
        if ("tap_xy".equals(type) || "click".equals(type)) {
            step.put("type", "click");
        }
        if (step.path("element_name").asText("").isBlank()) {
            String display = step.path("display_name").asText("");
            if (!display.isBlank()) {
                step.put("element_name", display.replaceAll("\\W+", "_").toLowerCase());
            }
        }
    }

    public OperationRecord pause(Long id) {
        OperationRecord rec = getWritable(id);
        rec.setStatus(OperationRecord.RecordStatus.paused);
        return recordRepository.save(rec);
    }

    public Map<String, Object> inspectDuringRecording(Long id, int x, int y, Integer displayWidth,
                                                     Integer displayHeight, boolean blocking) {
        return inspectForRecord(id, x, y, displayWidth, displayHeight, blocking);
    }

    public Map<String, Object> inspectForRecord(Long id, int x, int y, Integer displayWidth,
                                                Integer displayHeight, boolean blocking) {
        OperationRecord rec = get(id);
        String serial = rec.getDeviceSerial();
        if (serial == null || serial.isBlank()) {
            throw new AppException("INVALID", "设备序列号缺失", HttpStatus.BAD_REQUEST);
        }
        String platform = deviceRepository.findById(rec.getDeviceId())
                .map(d -> d.getPlatform().name())
                .orElse("android");
        if ((displayWidth == null || displayWidth <= 0) || (displayHeight == null || displayHeight <= 0)) {
            displayWidth = deviceRepository.findById(rec.getDeviceId())
                    .map(d -> d.getScreenWidth()).orElse(null);
            displayHeight = deviceRepository.findById(rec.getDeviceId())
                    .map(d -> d.getScreenHeight()).orElse(null);
        }
        // 录制中允许 blocking：执行器侧有 tap_priority 保护，不会长时间抢占点击；
        // 若强制非阻塞且无 UI 缓存，每一步都会变成「定位未识别」。
        Map<String, Object> inspect = executorClient.inspectPoint(
                serial, platform, x, y, displayWidth, displayHeight, blocking);
        boolean valid = isInspectRecognized(inspect);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("display_name", inspect.getOrDefault("display_name", ""));
        result.put("element_name", inspect.getOrDefault("element_name", ""));
        result.put("locators", inspect.getOrDefault("locators", Map.of()));
        result.put("locator_type", inspect.getOrDefault("locator_type", ""));
        result.put("locator_value", inspect.getOrDefault("locator_value", ""));
        result.put("widget_type", inspect.getOrDefault("widget_type", "unknown"));
        result.put("suggested_step_type", inspect.getOrDefault("suggested_step_type", "click"));
        result.put("source", inspect.getOrDefault("source", ""));
        if (inspect.containsKey("ui_width")) result.put("ui_width", inspect.get("ui_width"));
        if (inspect.containsKey("ui_height")) result.put("ui_height", inspect.get("ui_height"));
        if (inspect.containsKey("inspect_error")) {
            result.put("inspect_error", inspect.get("inspect_error"));
        }
        if (!valid) {
            String err = strVal(inspect.get("inspect_error"), "");
            if ("ui_dump_failed".equals(err)) {
                result.put("warning", "未识别到控件，已记录坐标点击");
            } else if ("executor_unavailable".equals(err)) {
                result.put("warning", "执行器不可用，无法识别控件");
            } else if (!"cache_miss".equals(err)) {
                result.put("warning", "未识别到稳定控件，将降级为坐标点击");
            }
        }
        return result;
    }

    public void warmInspectCache(Long id) {
        OperationRecord rec = get(id);
        String serial = rec.getDeviceSerial();
        if (serial == null || serial.isBlank()) return;
        String platform = deviceRepository.findById(rec.getDeviceId())
                .map(d -> d.getPlatform().name())
                .orElse("android");
        // 同步预热：保证录制点击时有可用 UI 树缓存
        executorClient.warmUiCache(serial, platform, true);
    }

    public OperationRecord resume(Long id) {
        OperationRecord rec = get(id);
        if (rec.getStatus() != OperationRecord.RecordStatus.paused) {
            throw new AppException("INVALID", "录制未处于暂停状态", HttpStatus.BAD_REQUEST);
        }
        rec.setStatus(OperationRecord.RecordStatus.recording);
        return recordRepository.save(rec);
    }

    @Transactional
    public OperationRecord markSegment(Long id, String label) {
        OperationRecord rec = getWritable(id);
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ObjectNode meta = root.has("meta") ? (ObjectNode) root.get("meta") : root.putObject("meta");
            ArrayNode segments = meta.has("segments") ? (ArrayNode) meta.get("segments") : meta.putArray("segments");
            ObjectNode seg = objectMapper.createObjectNode();
            long started = meta.path("started_at").asLong(System.currentTimeMillis());
            seg.put("offset_ms", Math.max(0, System.currentTimeMillis() - started));
            seg.put("label", label != null && !label.isBlank() ? label : "切点 " + (segments.size() + 1));
            seg.put("step_index", root.path("steps").size());
            seg.put("timestamp", System.currentTimeMillis());
            segments.add(seg);
            rec.setStepsJson(objectMapper.writeValueAsString(root));
            return recordRepository.save(rec);
        } catch (Exception e) {
            throw new AppException("INVALID", "标记切点失败", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public OperationRecord finish(Long id, boolean denoise) {
        OperationRecord rec = get(id);
        if (rec.getStatus() != OperationRecord.RecordStatus.recording
                && rec.getStatus() != OperationRecord.RecordStatus.paused) {
            throw new AppException("INVALID", "录制已结束", HttpStatus.BAD_REQUEST);
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ArrayNode steps = (ArrayNode) root.get("steps");
            if (denoise && steps != null) {
                root.set("steps", denoiseSteps(steps));
            }
            enrichRecordedSteps(root, rec.getDeviceSerial(), rec.getDeviceId());
            finalizeVisualSteps(root);
            applyStandardTemplate(root, rec.getDeviceId());
            computeRecognitionMetrics(root);
            ObjectNode meta = root.has("meta") ? (ObjectNode) root.get("meta") : root.putObject("meta");
            meta.put("finished_at", System.currentTimeMillis());
            rec.setStepsJson(objectMapper.writeValueAsString(root));
            rec.setStatus(OperationRecord.RecordStatus.completed);
            rec.setFinishedAt(LocalDateTime.now());
            return recordRepository.save(rec);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("INVALID", "结束录制失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public OperationRecord cancel(Long id) {
        OperationRecord rec = get(id);
        if (rec.getStatus() == OperationRecord.RecordStatus.completed
                || rec.getStatus() == OperationRecord.RecordStatus.cancelled) {
            throw new AppException("INVALID", "录制已结束", HttpStatus.BAD_REQUEST);
        }
        rec.setStatus(OperationRecord.RecordStatus.cancelled);
        rec.setFinishedAt(LocalDateTime.now());
        return recordRepository.save(rec);
    }

    public OperationRecord get(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "录制会话不存在", HttpStatus.NOT_FOUND));
    }

    public PageResult<Map<String, Object>> list(int page, int pageSize, String status) {
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(pageSize, 100)));
        Page<OperationRecord> result;
        if (status != null && !status.isBlank()) {
            try {
                OperationRecord.RecordStatus st = OperationRecord.RecordStatus.valueOf(status.trim());
                result = recordRepository.findByStatusOrderByCreatedAtDesc(st, pageable);
            } catch (IllegalArgumentException e) {
                result = recordRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
        } else {
            result = recordRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (OperationRecord rec : result.getContent()) {
            list.add(toSummary(rec));
        }
        return new PageResult<>(list, result.getTotalElements(), page, pageSize);
    }

    public Map<String, Object> auditSummary(int limit) {
        int sample = Math.max(1, Math.min(limit, 100));
        Page<OperationRecord> page = recordRepository.findByStatusOrderByCreatedAtDesc(
                OperationRecord.RecordStatus.completed, PageRequest.of(0, sample));
        List<Map<String, Object>> sessions = new ArrayList<>();
        double recognitionSum = 0;
        int recognitionCount = 0;
        double startupSum = 0;
        int startupCount = 0;
        int cpuOkCount = 0;
        int perfSampleCount = 0;
        Map<String, Integer> gradeCounts = new LinkedHashMap<>();
        gradeCounts.put("good", 0);
        gradeCounts.put("fair", 0);
        gradeCounts.put("heavy", 0);
        List<Map<String, Object>> belowThreshold = new ArrayList<>();
        double minRecognition = recordingConfigService.getMinRecognitionRate();
        final int startupThresholdMs = 3000;
        final double cpuOkRateMin = 80.0;
        long maxStartupMs = 0;
        List<Map<String, Object>> sessionsOverStartup = new ArrayList<>();
        List<Map<String, Object>> longSessions = new ArrayList<>();
        for (OperationRecord rec : page.getContent()) {
            Map<String, Object> summary = toSummary(rec);
            sessions.add(summary);
            Double rate = (Double) summary.get("recognition_rate");
            if (rate != null) {
                recognitionSum += rate;
                recognitionCount++;
                if (rate < minRecognition) {
                    belowThreshold.add(Map.of(
                            "id", rec.getId(),
                            "recognition_rate", rate,
                            "module_name", summary.getOrDefault("module_name", "")
                    ));
                }
            }
            Long startupMs = (Long) summary.get("startup_ms");
            if (startupMs != null && startupMs > 0) {
                startupSum += startupMs;
                startupCount++;
                maxStartupMs = Math.max(maxStartupMs, startupMs);
                if (startupMs > startupThresholdMs) {
                    sessionsOverStartup.add(Map.of(
                            "id", rec.getId(),
                            "startup_ms", startupMs,
                            "module_name", summary.getOrDefault("module_name", "")
                    ));
                }
            }
            Integer durationSec = (Integer) summary.get("duration_seconds");
            if (durationSec != null && durationSec >= 300) {
                longSessions.add(summary);
            }
            String grade = (String) summary.get("performance_grade");
            if (grade != null && !grade.isBlank()) {
                perfSampleCount++;
                gradeCounts.put(grade, gradeCounts.getOrDefault(grade, 0) + 1);
                if (Boolean.TRUE.equals(summary.get("cpu_ok"))) {
                    cpuOkCount++;
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sample_count", page.getNumberOfElements());
        out.put("avg_recognition_rate", recognitionCount > 0
                ? Math.round(recognitionSum * 100.0 / recognitionCount) / 100.0 : null);
        out.put("avg_startup_ms", startupCount > 0
                ? Math.round(startupSum / startupCount) : null);
        out.put("cpu_ok_rate", perfSampleCount > 0
                ? Math.round(cpuOkCount * 10000.0 / perfSampleCount) / 100.0 : null);
        out.put("performance_grade_counts", gradeCounts);
        out.put("sessions_below_threshold", belowThreshold);
        out.put("sessions", sessions);
        out.put("min_recognition_rate", minRecognition);
        out.put("min_locator_hit_rate", recordingConfigService.getMinLocatorHitRate());
        boolean recognitionPassed = recognitionCount > 0
                && (recognitionSum / recognitionCount) >= minRecognition;
        out.put("recognition_passed", recognitionPassed);
        double avgStartup = startupCount > 0 ? startupSum / startupCount : -1;
        boolean startupPassed = startupCount == 0
                || (avgStartup <= startupThresholdMs && sessionsOverStartup.isEmpty());
        boolean cpuPassed = perfSampleCount == 0
                || (cpuOkCount * 100.0 / perfSampleCount) >= cpuOkRateMin;
        List<String> failedChecks = new ArrayList<>();
        if (!recognitionPassed) {
            failedChecks.add("recognition_rate_below_threshold");
        }
        if (!startupPassed) {
            failedChecks.add("startup_over_3000ms");
        }
        if (!cpuPassed) {
            failedChecks.add("cpu_ok_rate_below_80");
        }
        Map<String, Object> p0 = new LinkedHashMap<>();
        p0.put("startup_threshold_ms", startupThresholdMs);
        p0.put("cpu_ok_rate_min", cpuOkRateMin);
        p0.put("max_startup_ms", maxStartupMs > 0 ? maxStartupMs : null);
        p0.put("startup_passed", startupPassed);
        p0.put("cpu_passed", cpuPassed);
        p0.put("overall_passed", failedChecks.isEmpty());
        p0.put("failed_checks", failedChecks);
        out.put("p0_acceptance", p0);
        out.put("sessions_over_startup", sessionsOverStartup);
        out.put("long_sessions", longSessions);
        out.put("long_session_count", longSessions.size());
        return out;
    }

    public Map<String, Object> validateStepLocators(Long id) {
        OperationRecord rec = get(id);
        if (rec.getStatus() != OperationRecord.RecordStatus.completed) {
            throw new AppException("INVALID", "仅已完成录制可校验定位", HttpStatus.BAD_REQUEST);
        }
        String serial = rec.getDeviceSerial();
        if (serial == null || serial.isBlank()) {
            throw new AppException("INVALID", "设备序列号缺失，无法校验", HttpStatus.BAD_REQUEST);
        }
        String platform = rec.getDeviceId() != null
                ? deviceRepository.findById(rec.getDeviceId()).map(d -> d.getPlatform().name()).orElse("android")
                : "android";
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ArrayNode steps = root.has("steps") ? (ArrayNode) root.get("steps") : objectMapper.createArrayNode();
            List<Map<String, Object>> details = new ArrayList<>();
            int checkable = 0;
            int hits = 0;
            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                if (!step.path("enabled").asBoolean(true)) continue;
                JsonNode locNode = step.get("locators");
                Map<String, Object> locMap = null;
                if (locNode != null && locNode.isObject() && locNode.size() > 0) {
                    locMap = objectMapper.convertValue(locNode, Map.class);
                } else if (!step.path("locator_type").asText("").isBlank()) {
                    locMap = new LinkedHashMap<>();
                    locMap.put(step.path("locator_type").asText(), step.path("locator_value").asText());
                }
                if (locMap == null || locMap.isEmpty()) continue;
                checkable++;
                Map<String, Object> vr = executorClient.validateLocatorOnScreen(serial, platform, locMap);
                boolean ok = Boolean.TRUE.equals(vr.get("valid"));
                if (ok) hits++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("step_index", i);
                row.put("type", step.path("type").asText(""));
                row.put("element_name", step.path("element_name").asText(""));
                row.put("valid", ok);
                row.put("matched_by", vr.getOrDefault("matched_by", ""));
                row.put("error", vr.getOrDefault("error", ""));
                details.add(row);
            }
            double hitRate = checkable > 0
                    ? Math.round(hits * 10000.0 / checkable) / 100.0 : 0.0;
            double threshold = recordingConfigService.getMinLocatorHitRate();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("checkable", checkable);
            out.put("hits", hits);
            out.put("locator_hit_rate", hitRate);
            out.put("threshold", threshold);
            out.put("passed", checkable > 0 && hitRate >= threshold);
            out.put("details", details);
            return out;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("INVALID", "定位校验失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> toSummary(OperationRecord rec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rec.getId());
        m.put("device_id", rec.getDeviceId());
        m.put("device_serial", rec.getDeviceSerial() != null ? rec.getDeviceSerial() : "");
        m.put("module_name", rec.getModuleName() != null ? rec.getModuleName() : "");
        m.put("version_label", rec.getVersionLabel() != null ? rec.getVersionLabel() : "");
        m.put("project_code", rec.getProjectCode() != null ? rec.getProjectCode() : "");
        m.put("recording_id", rec.getRecordingId());
        m.put("status", rec.getStatus().name());
        m.put("created_at", rec.getCreatedAt());
        m.put("finished_at", rec.getFinishedAt());
        m.put("step_count", 0);
        try {
            if (rec.getStepsJson() != null && !rec.getStepsJson().isBlank()) {
                JsonNode root = objectMapper.readTree(rec.getStepsJson());
                JsonNode steps = root.path("steps");
                if (steps.isArray()) m.put("step_count", steps.size());
                JsonNode meta = root.path("meta");
                if (meta.has("recognition_rate")) m.put("recognition_rate", meta.path("recognition_rate").asDouble());
                if (meta.has("locator_hit_rate")) m.put("locator_hit_rate", meta.path("locator_hit_rate").asDouble());
                if (meta.has("startup_ms")) m.put("startup_ms", meta.path("startup_ms").asLong());
                if (meta.has("record_fps_avg")) m.put("record_fps_avg", meta.path("record_fps_avg").asDouble());
                if (meta.has("paint_ms_avg")) m.put("paint_ms_avg", meta.path("paint_ms_avg").asDouble());
                if (meta.has("performance_grade")) m.put("performance_grade", meta.path("performance_grade").asText());
                if (meta.has("cpu_ok")) m.put("cpu_ok", meta.path("cpu_ok").asBoolean());
                if (meta.has("long_task_count")) m.put("long_task_count", meta.path("long_task_count").asInt());
                if (meta.has("task_id")) m.put("task_id", meta.path("task_id").asLong());
            }
        } catch (Exception ignored) {
            // ignore malformed steps_json in list view
        }
        if (rec.getRecordingId() != null) {
            recordingRepository.findById(rec.getRecordingId()).ifPresent(r -> {
                m.put("duration_seconds", r.getDurationSeconds());
                m.put("watermark_hash", r.getWatermarkHash());
            });
        }
        return m;
    }

    public Map<String, Object> previewDenoise(Long id) {
        OperationRecord rec = get(id);
        if (rec.getStatus() != OperationRecord.RecordStatus.recording
                && rec.getStatus() != OperationRecord.RecordStatus.paused) {
            throw new AppException("INVALID", "仅录制中可预览降噪", HttpStatus.BAD_REQUEST);
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ArrayNode steps = root.has("steps") ? (ArrayNode) root.get("steps") : objectMapper.createArrayNode();
            ArrayNode cleaned = denoiseSteps(steps);
            return Map.of(
                    "original_count", steps.size(),
                    "cleaned_count", cleaned.size(),
                    "removed_count", Math.max(0, steps.size() - cleaned.size()),
                    "original_steps", steps,
                    "cleaned_steps", cleaned
            );
        } catch (Exception e) {
            throw new AppException("INVALID", "预览降噪失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public OperationRecord updateSteps(Long id, Map<String, Object> body) {
        OperationRecord rec = get(id);
        if (rec.getStatus() != OperationRecord.RecordStatus.completed) {
            throw new AppException("INVALID", "仅已完成录制可编辑步骤", HttpStatus.BAD_REQUEST);
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            if (body.containsKey("steps")) {
                root.set("steps", objectMapper.valueToTree(body.get("steps")));
            }
            if (body.containsKey("meta") && body.get("meta") instanceof Map<?, ?> metaMap) {
                ObjectNode meta = root.has("meta") ? (ObjectNode) root.get("meta") : root.putObject("meta");
                metaMap.forEach((k, v) -> meta.set(String.valueOf(k), objectMapper.valueToTree(v)));
            }
            rec.setStepsJson(objectMapper.writeValueAsString(root));
            return recordRepository.save(rec);
        } catch (Exception e) {
            throw new AppException("INVALID", "更新步骤失败", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public OperationRecord emergencySave(Long id, String reason) {
        OperationRecord rec = get(id);
        if (rec.getStatus() == OperationRecord.RecordStatus.completed
                || rec.getStatus() == OperationRecord.RecordStatus.cancelled) {
            return rec;
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ArrayNode steps = root.has("steps") ? (ArrayNode) root.get("steps") : objectMapper.createArrayNode();
            root.set("steps", denoiseSteps(steps));
            enrichRecordedSteps(root, rec.getDeviceSerial(), rec.getDeviceId());
            finalizeVisualSteps(root);
            applyStandardTemplate(root, rec.getDeviceId());
            computeRecognitionMetrics(root);
            ObjectNode meta = root.has("meta") ? (ObjectNode) root.get("meta") : root.putObject("meta");
            meta.put("emergency", true);
            meta.put("emergency_reason", reason != null ? reason : "unknown");
            meta.put("finished_at", System.currentTimeMillis());
            rec.setStepsJson(objectMapper.writeValueAsString(root));
            rec.setStatus(OperationRecord.RecordStatus.completed);
            rec.setFinishedAt(LocalDateTime.now());
            return recordRepository.save(rec);
        } catch (Exception e) {
            throw new AppException("INVALID", "应急保存失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public Map<String, Object> uploadVideo(Long id, MultipartFile file, Integer durationSeconds,
                                           String operatorLabel, Long userId, MultipartFile thumbnail,
                                           String moduleName, String versionLabel, String projectCode,
                                           String cropRectJson, String clientMetricsJson) {
        OperationRecord rec = get(id);
        if (file == null || file.isEmpty()) {
            throw new AppException("INVALID", "视频文件为空", HttpStatus.BAD_REQUEST);
        }
        try {
            String operator = resolveOperatorLabel(operatorLabel, userId);
            String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            String fileName = "session_" + id + "_" + ts + ".webm";
            Path base = Path.of(properties.getStorage().getLocalPath(), "sessions").toAbsolutePath().normalize();
            Files.createDirectories(base);
            Path target = base.resolve(fileName).normalize();
            if (!target.startsWith(base)) {
                throw new SecurityException("非法路径");
            }
            file.transferTo(target);
            long size = Files.size(target);

            String mod = firstNonBlank(moduleName, rec.getModuleName());
            String ver = firstNonBlank(versionLabel, rec.getVersionLabel());
            String proj = firstNonBlank(projectCode, rec.getProjectCode());
            if (mod == null || ver == null || proj == null) {
                JsonNode metaNode = objectMapper.readTree(rec.getStepsJson()).path("meta");
                if (mod == null && metaNode.has("module_name")) mod = metaNode.path("module_name").asText(null);
                if (ver == null && metaNode.has("version_label")) ver = metaNode.path("version_label").asText(null);
                if (proj == null && metaNode.has("project_code")) proj = metaNode.path("project_code").asText(null);
            }

            String watermarkPayload = operator + "|" + ts + "|session:" + id
                    + (proj != null && !proj.isBlank() ? "|proj:" + proj : "");
            String watermarkHash = sha256(watermarkPayload);

            RecordingResource recording = new RecordingResource();
            recording.setDeviceId(rec.getDeviceId());
            recording.setFileName(fileName);
            recording.setFilePath("sessions/" + fileName);
            recording.setFileSize(size);
            recording.setDurationSeconds(durationSeconds != null ? durationSeconds : 0);
            recording.setWatermarkHash(watermarkHash);
            recording.setWatermarkPayload(watermarkPayload);
            recording.setOperatorLabel(operator);
            recording.setModuleName(mod);
            recording.setVersionLabel(ver);
            recording.setProjectCode(proj);
            recording.setSessionId(id);
            if (cropRectJson != null && !cropRectJson.isBlank()) {
                recording.setCropRectJson(cropRectJson);
            }
            recording.setFormat("webm");
            recording.setStatus(RecordingResource.RecordingStatus.ready);

            if (thumbnail != null && !thumbnail.isEmpty()) {
                String thumbName = "session_" + id + "_" + ts + "_thumb.jpg";
                Path thumbTarget = base.resolve(thumbName).normalize();
                thumbnail.transferTo(thumbTarget);
                recording.setThumbnailPath("sessions/" + thumbName);
            }

            recording = recordingRepository.save(recording);

            rec.setRecordingId(recording.getId());
            if (mod != null) rec.setModuleName(mod);
            if (ver != null) rec.setVersionLabel(ver);
            if (proj != null) rec.setProjectCode(proj);

            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ObjectNode meta = root.has("meta") ? (ObjectNode) root.get("meta") : root.putObject("meta");
            meta.put("recording_id", recording.getId());
            meta.put("video_duration_seconds", durationSeconds != null ? durationSeconds : 0);
            meta.put("operator_label", operator);
            meta.put("watermark_hash", watermarkHash);
            if (mod != null) meta.put("module_name", mod);
            if (ver != null) meta.put("version_label", ver);
            if (proj != null) meta.put("project_code", proj);
            mergeClientMetrics(meta, clientMetricsJson);
            rec.setStepsJson(objectMapper.writeValueAsString(root));
            recordRepository.save(rec);

            return Map.of(
                    "recording_id", recording.getId(),
                    "file_name", fileName,
                    "file_size", size,
                    "watermark_hash", watermarkHash,
                    "module_name", mod != null ? mod : "",
                    "version_label", ver != null ? ver : "",
                    "project_code", proj != null ? proj : "",
                    "thumbnail_path", recording.getThumbnailPath() != null ? recording.getThumbnailPath() : ""
            );
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("INVALID", "视频上传失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public TestCase toCase(Long recordId, Map<String, Object> meta, Long userId) {
        OperationRecord rec = get(recordId);
        if (rec.getStatus() != OperationRecord.RecordStatus.completed) {
            throw new AppException("INVALID", "请先完成录制", HttpStatus.BAD_REQUEST);
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ObjectNode stepsMeta = root.has("meta") ? (ObjectNode) root.get("meta") : objectMapper.createObjectNode();
            if (meta.containsKey("precondition")) {
                stepsMeta.put("precondition", meta.get("precondition").toString());
            }
            if (meta.containsKey("expected_result")) {
                stepsMeta.put("expected_result", meta.get("expected_result").toString());
            }
            if (meta.containsKey("environment")) {
                stepsMeta.put("environment", meta.get("environment").toString());
            }
            root.set("meta", stepsMeta);

            Map<String, Object> body = new HashMap<>();
            body.put("name", meta.getOrDefault("name", "录制用例"));
            body.put("steps_content", objectMapper.writeValueAsString(root));
            body.put("script_type", "visual");
            body.put("platform", meta.getOrDefault("platform", stepsMeta.path("platform").asText("android")));
            body.put("app_package", meta.get("app_package"));
            body.put("case_status", "draft");
            if (meta.containsKey("folder_id")) body.put("folder_id", meta.get("folder_id"));
            if (stepsMeta.has("recording_id")) {
                body.put("description", "关联录屏 #" + stepsMeta.get("recording_id").asLong());
            }
            return caseCatalogService.createCase(body, userId);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("INVALID", "生成用例失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public List<TestCase> toCasesBySegments(Long recordId, Map<String, Object> meta, Long userId) {
        OperationRecord rec = get(recordId);
        if (rec.getStatus() != OperationRecord.RecordStatus.completed) {
            throw new AppException("INVALID", "请先完成录制", HttpStatus.BAD_REQUEST);
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(rec.getStepsJson());
            ObjectNode stepsMeta = root.has("meta") ? (ObjectNode) root.get("meta") : objectMapper.createObjectNode();
            ArrayNode segments = stepsMeta.has("segments") ? (ArrayNode) stepsMeta.get("segments") : objectMapper.createArrayNode();
            ArrayNode allSteps = root.has("steps") ? (ArrayNode) root.get("steps") : objectMapper.createArrayNode();
            if (segments.isEmpty()) {
                return List.of(toCase(recordId, meta, userId));
            }
            List<TestCase> cases = new ArrayList<>();
            String baseName = meta.getOrDefault("name", "录制用例").toString();
            for (int i = 0; i < segments.size(); i++) {
                int startIdx = segments.get(i).path("step_index").asInt(0);
                int endIdx = (i + 1 < segments.size())
                        ? segments.get(i + 1).path("step_index").asInt(allSteps.size())
                        : allSteps.size();
                ArrayNode slice = objectMapper.createArrayNode();
                for (int j = startIdx; j < endIdx && j < allSteps.size(); j++) {
                    slice.add(allSteps.get(j).deepCopy());
                }
                ObjectNode caseRoot = objectMapper.createObjectNode();
                caseRoot.put("version", 2);
                ObjectNode caseMeta = stepsMeta.deepCopy();
                caseMeta.put("segment_index", i + 1);
                caseMeta.put("segment_label", segments.get(i).path("label").asText("切点 " + (i + 1)));
                caseRoot.set("meta", caseMeta);
                caseRoot.set("steps", slice);

                Map<String, Object> body = new HashMap<>();
                body.put("name", baseName + " - " + segments.get(i).path("label").asText("场景" + (i + 1)));
                body.put("steps_content", objectMapper.writeValueAsString(caseRoot));
                body.put("script_type", "visual");
                body.put("platform", meta.getOrDefault("platform", stepsMeta.path("platform").asText("android")));
                body.put("app_package", meta.get("app_package"));
                body.put("case_status", "draft");
                if (meta.containsKey("folder_id")) body.put("folder_id", meta.get("folder_id"));
                cases.add(caseCatalogService.createCase(body, userId));
            }
            return cases;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("INVALID", "分段生成用例失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public TestCase toAutomationCase(Long recordId, Map<String, Object> meta, Long userId) {
        Map<String, Object> payload = new HashMap<>(meta);
        payload.put("case_status", "active");
        TestCase created = toCase(recordId, payload, userId);
        Map<String, Object> patch = new HashMap<>();
        patch.put("case_status", "active");
        patch.put("enable_recording", true);
        if (meta.containsKey("human_delay")) {
            patch.put("human_delay", meta.get("human_delay"));
        }
        return caseCatalogService.updateCase(created.getId(), patch, userId);
    }

    private OperationRecord getWritable(Long id) {
        OperationRecord rec = get(id);
        if (rec.getStatus() != OperationRecord.RecordStatus.recording
                && rec.getStatus() != OperationRecord.RecordStatus.paused) {
            throw new AppException("INVALID", "录制已结束", HttpStatus.BAD_REQUEST);
        }
        return rec;
    }

    private ArrayNode denoiseSteps(ArrayNode steps) {
        ArrayNode result = objectMapper.createArrayNode();
        long lastTs = 0;
        String lastType = "";
        String lastText = "";
        for (JsonNode step : steps) {
            long ts = step.path("timestamp").asLong(0);
            String type = step.path("type").asText("");
            if ("click".equals(type)) {
                if (ts - lastTs < 300 && type.equals(lastType)) continue;
                if (step.path("x").asInt() == 0 && step.path("y").asInt() == 0) continue;
            }
            if ("swipe".equals(type)) {
                int dx = Math.abs(step.path("x2").asInt() - step.path("x").asInt());
                int dy = Math.abs(step.path("y2").asInt() - step.path("y").asInt());
                if (dx < 15 && dy < 15) continue;
            }
            if ("input".equals(type)) {
                String text = step.path("text").asText("");
                if (text.isBlank()) continue;
                if ("input".equals(lastType) && text.equals(lastText) && ts - lastTs < 500) continue;
                lastText = text;
            }
            if ("wait".equals(type)) continue;
            result.add(step);
            lastTs = ts;
            lastType = type;
        }
        return result;
    }

    private void enrichRecordedSteps(ObjectNode root, String serial, Long deviceId) {
        ArrayNode steps = root.has("steps") ? (ArrayNode) root.get("steps") : null;
        if (steps == null || steps.isEmpty() || serial == null || serial.isBlank()) {
            return;
        }
        String platform = "android";
        if (deviceId != null) {
            platform = deviceRepository.findById(deviceId)
                    .map(d -> d.getPlatform().name())
                    .orElse("android");
        }
        if (!"android".equals(platform)) {
            return;
        }
        ArrayNode enriched = objectMapper.createArrayNode();
        for (JsonNode step : steps) {
            String type = step.path("type").asText("");
            if ("click".equals(type)) {
                int sizeBefore = enriched.size();
                enriched.add(convertLiveInspectStep(step));
                if (step.has("snapshot_thumb") && enriched.size() > sizeBefore) {
                    JsonNode created = enriched.get(enriched.size() - 1);
                    if (created instanceof ObjectNode createdNode && !createdNode.has("snapshot_thumb")) {
                        createdNode.put("snapshot_thumb", step.path("snapshot_thumb").asText());
                    }
                }
            } else if ("key".equals(type)) {
                ObjectNode v = objectMapper.createObjectNode();
                v.put("type", "press_key");
                v.put("key", step.path("key").asText("back"));
                v.put("enabled", true);
                if (step.has("video_offset_ms")) v.put("video_offset_ms", step.path("video_offset_ms").asLong());
                enriched.add(v);
            } else {
                enriched.add(step.deepCopy());
            }
        }
        root.set("steps", enriched);
    }

    private Integer[] resolveDisplaySize(ObjectNode root, Long deviceId) {
        ObjectNode meta = root.has("meta") ? (ObjectNode) root.get("meta") : null;
        int dw = meta != null ? meta.path("display_width").asInt(0) : 0;
        int dh = meta != null ? meta.path("display_height").asInt(0) : 0;
        if ((dw <= 0 || dh <= 0) && deviceId != null) {
            var deviceOpt = deviceRepository.findById(deviceId);
            if (deviceOpt.isPresent()) {
                Device device = deviceOpt.get();
                if (dw <= 0 && device.getScreenWidth() != null) dw = device.getScreenWidth();
                if (dh <= 0 && device.getScreenHeight() != null) dh = device.getScreenHeight();
            }
        }
        return new Integer[]{dw > 0 ? dw : null, dh > 0 ? dh : null};
    }

    @SuppressWarnings("unchecked")
    private void appendEnrichedClick(ArrayNode enriched, Map<String, Object> inspect, int x, int y,
                                     String lastContext, long videoOffsetMs) {
        String context = strVal(inspect.get("context"), "native");
        if (("webview".equals(context) || "hybrid".equals(context)) && !"webview".equals(lastContext)) {
            ObjectNode sw = objectMapper.createObjectNode();
            sw.put("type", "switch_context");
            sw.put("mode", "webview");
            sw.put("enabled", true);
            enriched.add(sw);
        }
        String source = strVal(inspect.get("source"), "coordinate");
        String elementName = strVal(inspect.get("element_name"), "");
        String displayName = strVal(inspect.get("display_name"), elementName);
        String text = strVal(inspect.get("text"), "");
        boolean valid = Boolean.TRUE.equals(inspect.get("valid"));
        ObjectNode visual = objectMapper.createObjectNode();
        visual.put("enabled", true);
        visual.put("disable_reason", "");
        visual.put("disable_mode", "");
        if (videoOffsetMs > 0) visual.put("video_offset_ms", videoOffsetMs);
        String suggested = strVal(inspect.get("suggested_step_type"), "click");
        if (inspect.containsKey("widget_type")) {
            visual.put("widget_type", strVal(inspect.get("widget_type"), "unknown"));
        }
        if ("uiautomator".equals(source) && isInspectRecognized(inspect)) {
            if (elementName.isBlank()) {
                elementName = strVal(inspect.get("display_name"), "ui_element");
                elementName = elementName.replaceAll("\\W+", "_").toLowerCase();
            }
            visual.put("type", resolveStepType(suggested));
            visual.put("element_name", elementName);
            visual.put("display_name", displayName);
            visual.put("x", x);
            visual.put("y", y);
            visual.put("locator_valid", true);
            if (inspect.get("locators") instanceof Map<?, ?> locMap) {
                visual.set("locators", objectMapper.valueToTree(locMap));
            }
            if (inspect.containsKey("locator_type")) {
                visual.put("locator_type", strVal(inspect.get("locator_type"), ""));
                visual.put("locator_value", strVal(inspect.get("locator_value"), ""));
            }
            enriched.add(visual);
            if (!text.isBlank() && !"input".equals(suggested) && !"rich_text".equals(strVal(inspect.get("widget_type"), ""))) {
                ObjectNode assertText = objectMapper.createObjectNode();
                assertText.put("type", "assert_text");
                assertText.put("expected", text);
                assertText.put("enabled", true);
                enriched.add(assertText);
            }
        } else if ("ocr".equals(source) && !text.isBlank()) {
            visual.put("type", "tap_ocr");
            visual.put("expected", text);
            visual.put("display_name", displayName);
            visual.put("x", x);
            visual.put("y", y);
            visual.put("locator_valid", true);
            enriched.add(visual);
        } else {
            ObjectNode tap = objectMapper.createObjectNode();
            tap.put("type", "tap_xy");
            tap.put("x", x);
            tap.put("y", y);
            tap.put("enabled", true);
            tap.put("disable_reason", "");
            tap.put("disable_mode", "");
            tap.put("needs_manual_fix", true);
            tap.put("locator_valid", false);
            if (videoOffsetMs > 0) tap.put("video_offset_ms", videoOffsetMs);
            enriched.add(tap);
        }
    }

    private ObjectNode convertLiveInspectStep(JsonNode step) {
        ObjectNode v = objectMapper.createObjectNode();
        v.put("enabled", true);
        v.put("disable_reason", "");
        v.put("disable_mode", "");
        if (step.has("video_offset_ms")) v.put("video_offset_ms", step.path("video_offset_ms").asLong());
        if (step.has("snapshot_thumb")) v.put("snapshot_thumb", step.path("snapshot_thumb").asText());
        if (step.has("widget_type")) v.put("widget_type", step.path("widget_type").asText());
        String elementName = step.path("element_name").asText("");
        String displayName = step.path("display_name").asText("");
        String suggested = step.path("suggested_step_type").asText("click");
        if (isRecognizedStep(step)) {
            if (elementName.isBlank()) {
                elementName = deriveElementName(step, displayName);
            }
            v.put("type", resolveStepType(suggested));
            v.put("element_name", elementName);
            if (!displayName.isBlank()) v.put("display_name", displayName);
            else if (step.has("display_name")) v.put("display_name", step.path("display_name").asText());
            if (step.has("locators")) v.set("locators", step.get("locators"));
            if (step.has("locator_type")) v.put("locator_type", step.path("locator_type").asText());
            if (step.has("locator_value")) v.put("locator_value", step.path("locator_value").asText());
            v.put("locator_valid", true);
        } else {
            v.put("type", "tap_xy");
            v.put("locator_valid", false);
            v.put("needs_manual_fix", true);
        }
        copyTapCoordinates(step, v);
        return v;
    }

    private void copyTapCoordinates(JsonNode from, ObjectNode to) {
        if (from.has("x") && !from.path("x").isNull()) to.put("x", from.path("x").asInt());
        if (from.has("y") && !from.path("y").isNull()) to.put("y", from.path("y").asInt());
    }

    private void appendTapXY(ArrayNode enriched, int x, int y, long videoOffsetMs) {
        ObjectNode visual = objectMapper.createObjectNode();
        visual.put("type", "tap_xy");
        visual.put("x", x);
        visual.put("y", y);
        visual.put("enabled", true);
        visual.put("disable_reason", "");
        visual.put("disable_mode", "");
        if (videoOffsetMs > 0) visual.put("video_offset_ms", videoOffsetMs);
        enriched.add(visual);
    }

    private void finalizeVisualSteps(ObjectNode root) {
        ArrayNode steps = (ArrayNode) root.get("steps");
        if (steps == null) return;
        ArrayNode visual = objectMapper.createArrayNode();
        for (JsonNode step : steps) {
            String type = step.path("type").asText("click");
            if (isVisualStepType(type)) {
                visual.add(step.deepCopy());
                continue;
            }
            ObjectNode v = objectMapper.createObjectNode();
            if ("swipe".equals(type)) {
                v.put("type", "swipe");
                v.put("x1", step.path("x").asInt());
                v.put("y1", step.path("y").asInt());
                v.put("x2", step.path("x2").asInt(500));
                v.put("y2", step.path("y2").asInt(400));
            } else if ("long_press".equals(type)) {
                v.put("type", "long_press");
                v.put("x", step.path("x").asInt());
                v.put("y", step.path("y").asInt());
                v.put("duration_ms", step.path("duration_ms").asInt(800));
            } else if ("input".equals(type)) {
                v.put("type", "input");
                String raw = step.path("text").asText("");
                v.put("text", DesensitizeUtil.desensitize(raw));
                if (DesensitizeUtil.containsSensitive(raw)) {
                    v.put("desensitized", true);
                }
            } else {
                if (step.has("element_name") && !step.path("element_name").asText("").isBlank()) {
                    String suggested = step.path("suggested_step_type").asText("click");
                    v.put("type", resolveStepType(suggested));
                    v.put("element_name", step.path("element_name").asText());
                    if (step.has("display_name")) v.put("display_name", step.path("display_name").asText());
                    if (step.has("widget_type")) v.put("widget_type", step.path("widget_type").asText());
                    if (step.has("locators")) v.set("locators", step.get("locators"));
                    copyTapCoordinates(step, v);
                } else {
                    v.put("type", "tap_xy");
                    copyTapCoordinates(step, v);
                }
            }
            v.put("enabled", true);
            v.put("disable_reason", "");
            v.put("disable_mode", "");
            if ("tap_xy".equals(v.path("type").asText()) && !step.path("locator_valid").asBoolean(false)
                    && step.path("element_name").asText("").isBlank()) {
                v.put("needs_manual_fix", true);
            }
            if (step.has("video_offset_ms")) v.put("video_offset_ms", step.path("video_offset_ms").asLong());
            if (step.has("locators")) v.set("locators", step.get("locators"));
            visual.add(v);
        }
        root.set("steps", visual);
    }

    private void applyStandardTemplate(ObjectNode root, Long deviceId) {
        ObjectNode meta = root.has("meta") ? (ObjectNode) root.get("meta") : root.putObject("meta");
        if (!meta.has("precondition") || meta.path("precondition").asText("").isBlank()) {
            meta.put("precondition", "设备已连接，被测应用已安装并可正常访问");
        }
        if (!meta.has("expected_result") || meta.path("expected_result").asText("").isBlank()) {
            meta.put("expected_result", "各操作步骤执行成功，页面响应符合预期");
        }
        if (!meta.has("environment") || meta.path("environment").asText("").isBlank()) {
            String platform = deviceId != null
                    ? deviceRepository.findById(deviceId).map(d -> d.getPlatform().name()).orElse("android")
                    : "android";
            meta.put("environment", platform);
        }
        if (!meta.has("validation_elements")) {
            meta.putArray("validation_elements");
        }
    }

    private boolean isVisualStepType(String type) {
        return Set.of("switch_context", "tap_xy", "tap_ocr", "assert_text", "click",
                "select", "confirm_dialog", "upload", "hover", "rich_text",
                "dismiss_popup", "check_anomaly", "swipe", "input", "press_key", "long_press").contains(type);
    }

    private boolean isInspectRecognized(Map<String, Object> inspect) {
        if (inspect == null || inspect.isEmpty()) return false;
        if (truthy(inspect.get("valid"))) return true;
        if (!strVal(inspect.get("element_name"), "").isBlank()) return true;
        if (!strVal(inspect.get("locator_value"), "").isBlank()) return true;
        if (!strVal(inspect.get("display_name"), "").isBlank()
                && !"未知控件".equals(strVal(inspect.get("display_name"), ""))) return true;
        Object locators = inspect.get("locators");
        if (locators instanceof Map<?, ?> loc && !loc.isEmpty()) return true;
        return false;
    }

    private boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(v.toString());
    }

    private boolean isRecognizedStep(JsonNode step) {
        if (step.path("locator_valid").asBoolean(false)) return true;
        if (!step.path("element_name").asText("").isBlank()) return true;
        if (step.has("locators") && step.get("locators").isObject() && step.get("locators").size() > 0) return true;
        if (!step.path("locator_value").asText("").isBlank()) return true;
        return false;
    }

    private String deriveElementName(JsonNode step, String displayName) {
        if (step.has("locators") && step.get("locators").isObject()) {
            JsonNode loc = step.get("locators");
            if (loc.has("id")) return loc.path("id").asText();
            if (loc.has("content_desc")) return loc.path("content_desc").asText().replaceAll("\\W+", "_").toLowerCase();
        }
        if (!displayName.isBlank()) {
            return displayName.replaceAll("\\W+", "_").toLowerCase();
        }
        return "ui_element";
    }

    private String resolveStepType(String suggested) {
        if (suggested == null || suggested.isBlank()) return "click";
        return isVisualStepType(suggested) ? suggested : "click";
    }

    private void computeRecognitionMetrics(ObjectNode root) {
        ArrayNode steps = root.has("steps") ? (ArrayNode) root.get("steps") : null;
        if (steps == null || steps.isEmpty()) return;
        ObjectNode meta = root.has("meta") ? (ObjectNode) root.get("meta") : root.putObject("meta");
        Set<String> interactiveTypes = Set.of("click", "tap_xy", "input", "rich_text", "hover",
                "select", "upload", "confirm_dialog", "tap_ocr", "long_press");
        int interactive = 0;
        int recognized = 0;
        int locatorHits = 0;
        ArrayNode validationElements = objectMapper.createArrayNode();
        for (JsonNode step : steps) {
            String type = step.path("type").asText("");
            if (!interactiveTypes.contains(type)) continue;
            interactive++;
            boolean hasElement = !step.path("element_name").asText("").isBlank();
            boolean validLocator = step.path("locator_valid").asBoolean(hasElement);
            boolean ocr = "tap_ocr".equals(type);
            if (hasElement || ocr || validLocator) recognized++;
            if (hasElement && validLocator) locatorHits++;
            if (hasElement) validationElements.add(step.path("element_name").asText());
        }
        if (interactive > 0) {
            meta.put("recognition_rate", Math.round(recognized * 10000.0 / interactive) / 100.0);
            meta.put("locator_hit_rate", Math.round(locatorHits * 10000.0 / interactive) / 100.0);
        } else {
            meta.put("recognition_rate", 0);
            meta.put("locator_hit_rate", 0);
        }
        meta.set("validation_elements", validationElements);
    }

    private String resolveOperatorLabel(String operatorLabel, Long userId) {
        if (operatorLabel != null && !operatorLabel.isBlank()) {
            return operatorLabel;
        }
        if (userId != null) {
            return userRepository.findById(userId)
                    .map(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                            ? u.getDisplayName() : u.getUsername())
                    .orElse("unknown");
        }
        return "unknown";
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private int intVal(Object o) {
        return o == null ? 0 : Integer.parseInt(o.toString());
    }

    private long longVal(Object o) {
        return o == null ? 0L : Long.parseLong(o.toString());
    }

    private String strVal(Object o, String def) {
        return o == null || o.toString().isBlank() ? def : o.toString();
    }

    private String strOpt(Map<String, Object> options, String key) {
        if (options == null || !options.containsKey(key)) return null;
        String v = options.get(key).toString().trim();
        return v.isEmpty() ? null : v;
    }

    private boolean isProductionRecording(OperationRecord rec) {
        String code = rec.getProjectCode() != null ? rec.getProjectCode().toLowerCase() : "";
        String version = rec.getVersionLabel() != null ? rec.getVersionLabel().toLowerCase() : "";
        return code.contains("prod") || code.contains("生产")
                || version.contains("prod") || version.contains("生产");
    }

    private void mergeClientMetrics(ObjectNode meta, String clientMetricsJson) {
        if (clientMetricsJson == null || clientMetricsJson.isBlank()) return;
        try {
            JsonNode metrics = objectMapper.readTree(clientMetricsJson);
            if (metrics.has("startup_ms")) meta.put("startup_ms", metrics.path("startup_ms").asLong());
            if (metrics.has("record_fps_avg")) meta.put("record_fps_avg", metrics.path("record_fps_avg").asDouble());
            if (metrics.has("paint_ms_avg")) meta.put("paint_ms_avg", metrics.path("paint_ms_avg").asDouble());
            if (metrics.has("performance_grade")) meta.put("performance_grade", metrics.path("performance_grade").asText());
            if (metrics.has("long_task_count")) meta.put("long_task_count", metrics.path("long_task_count").asInt());
            if (metrics.has("max_long_task_ms")) meta.put("max_long_task_ms", metrics.path("max_long_task_ms").asLong());
            if (metrics.has("long_tasks_per_min")) meta.put("long_tasks_per_min", metrics.path("long_tasks_per_min").asInt());
            if (metrics.has("cpu_ok")) meta.put("cpu_ok", metrics.path("cpu_ok").asBoolean());
        } catch (Exception ignored) {
            // ignore malformed client metrics
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private void mergeInspectIntoStep(ObjectNode step, Map<String, Object> inspect) {
        if (inspect == null || inspect.isEmpty()) return;
        boolean valid = Boolean.TRUE.equals(inspect.get("valid"));
        step.put("locator_valid", valid);
        if (inspect.containsKey("display_name")) {
            step.put("display_name", DesensitizeUtil.desensitize(strVal(inspect.get("display_name"), "")));
        }
        if (inspect.containsKey("element_name")) {
            String en = strVal(inspect.get("element_name"), "");
            if (en.isBlank() && isInspectRecognized(inspect)) {
                en = strVal(inspect.get("display_name"), "ui_element").replaceAll("\\W+", "_").toLowerCase();
            }
            if (!en.isBlank()) step.put("element_name", en);
        }
        if (inspect.containsKey("widget_type")) step.put("widget_type", strVal(inspect.get("widget_type"), "unknown"));
        if (inspect.containsKey("suggested_step_type")) {
            step.put("suggested_step_type", strVal(inspect.get("suggested_step_type"), "click"));
        }
        if (inspect.containsKey("locator_type")) step.put("locator_type", strVal(inspect.get("locator_type"), ""));
        if (inspect.containsKey("locator_value")) step.put("locator_value", strVal(inspect.get("locator_value"), ""));
        if (inspect.get("locators") != null) step.set("locators", objectMapper.valueToTree(inspect.get("locators")));
    }
}
