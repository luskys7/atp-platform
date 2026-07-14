package com.atp.platform.service;

import com.atp.platform.entity.OperationRecord;
import com.atp.platform.entity.RecordingResource;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.OperationRecordRepository;
import com.atp.platform.repository.RecordingResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecordingService {

    private final RecordingResourceRepository recordingRepository;
    private final RecordingStorageService storageService;
    private final OperationRecordRepository operationRecordRepository;
    private final ObjectMapper objectMapper;
    public Page<RecordingResource> list(int page, int pageSize, Long taskId, Long deviceId,
                                      String moduleName, String versionLabel, String keyword) {
        PageRequest pageable = PageRequest.of(page - 1, pageSize);
        return recordingRepository.search(taskId, deviceId, moduleName, versionLabel, keyword, pageable);
    }

    public String getPlaybackUrl(Long id) {
        RecordingResource rec = recordingRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "录屏不存在", HttpStatus.NOT_FOUND));
        if (rec.getStatus() != RecordingResource.RecordingStatus.ready) {
            throw new AppException("INVALID", "录屏尚未就绪", HttpStatus.BAD_REQUEST);
        }
        try {
            if (storageService instanceof LocalRecordingStorageService) {
                return "/api/v1/recordings/" + id + "/stream";
            }
            return storageService.getPlaybackUrl(rec.getFilePath());
        } catch (Exception e) {
            throw new AppException("E2002", "获取回放地址失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void delete(Long id) {
        recordingRepository.deleteById(id);
    }

    /** 录屏回放上下文：步骤 markers、标注、指标（来自关联 operation_record） */
    public Map<String, Object> getPlaybackContext(Long id) {
        RecordingResource rec = recordingRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "录屏不存在", HttpStatus.NOT_FOUND));
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("recording_id", id);
        ctx.put("session_id", rec.getSessionId());
        ctx.put("markers", List.of());
        ctx.put("annotations", List.of());
        if (rec.getSessionId() == null) {
            return ctx;
        }
        OperationRecord session = operationRecordRepository.findById(rec.getSessionId()).orElse(null);
        if (session == null || session.getStepsJson() == null || session.getStepsJson().isBlank()) {
            return ctx;
        }
        try {
            JsonNode root = objectMapper.readTree(session.getStepsJson());
            JsonNode meta = root.path("meta");
            if (meta.has("video_annotations")) {
                ctx.put("annotations", objectMapper.convertValue(meta.get("video_annotations"), List.class));
            }
            if (meta.has("recognition_rate")) ctx.put("recognition_rate", meta.path("recognition_rate").asDouble());
            if (meta.has("startup_ms")) ctx.put("startup_ms", meta.path("startup_ms").asLong());
            if (meta.has("record_fps_avg")) ctx.put("record_fps_avg", meta.path("record_fps_avg").asDouble());
            if (meta.has("paint_ms_avg")) ctx.put("paint_ms_avg", meta.path("paint_ms_avg").asDouble());
            if (meta.has("performance_grade")) ctx.put("performance_grade", meta.path("performance_grade").asText());
            if (meta.has("cpu_ok")) ctx.put("cpu_ok", meta.path("cpu_ok").asBoolean());
            if (meta.has("long_task_count")) ctx.put("long_task_count", meta.path("long_task_count").asInt());
            if (meta.has("task_id")) ctx.put("task_id", meta.path("task_id").asLong());
            List<Map<String, Object>> markers = new ArrayList<>();
            JsonNode steps = root.path("steps");
            if (steps.isArray()) {
                for (int i = 0; i < steps.size(); i++) {
                    JsonNode step = steps.get(i);
                    if (!step.has("video_offset_ms")) continue;
                    Map<String, Object> m = new HashMap<>();
                    m.put("index", i);
                    m.put("offsetMs", step.path("video_offset_ms").asLong());
                    String label = step.path("display_name").asText("");
                    if (label.isBlank()) label = step.path("element_name").asText(step.path("type").asText("步骤"));
                    m.put("label", (i + 1) + ". " + label);
                    markers.add(m);
                }
            }
            ctx.put("markers", markers);
            ctx.put("review_url", "/recordings/review/" + rec.getSessionId());
        } catch (Exception e) {
            throw new AppException("INVALID", "解析录制上下文失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return ctx;
    }

    /** 水印 hash 与 payload 一致性核验（S2-TC07） */
    public Map<String, Object> verifyWatermark(Long id) {
        RecordingResource rec = recordingRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "录屏不存在", HttpStatus.NOT_FOUND));
        String payload = rec.getWatermarkPayload();
        String stored = rec.getWatermarkHash();
        Map<String, Object> out = new HashMap<>();
        out.put("recording_id", id);
        out.put("watermark_payload", payload != null ? payload : "");
        out.put("watermark_hash", stored != null ? stored : "");
        if (payload == null || payload.isBlank() || stored == null || stored.isBlank()) {
            out.put("verified", false);
            out.put("reason", "missing_payload_or_hash");
            return out;
        }
        String computed = sha256(payload);
        out.put("computed_hash", computed);
        out.put("verified", computed.equalsIgnoreCase(stored));
        return out;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}