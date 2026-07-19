package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.OperationRecord;
import com.atp.platform.entity.RecordingResource;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.OperationRecordRepository;
import com.atp.platform.repository.RecordingResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class RecordingService {

    public static final int DEFAULT_RETAIN_DAYS = 90;
    public static final int EXPIRE_WARN_DAYS = 7;

    private final RecordingResourceRepository recordingRepository;
    private final RecordingStorageService storageService;
    private final OperationRecordRepository operationRecordRepository;
    private final ObjectMapper objectMapper;
    private final AtpProperties properties;
    private final ObjectProvider<LocalRecordingStorageService> localStorageProvider;

    public Page<RecordingResource> list(int page, int pageSize, Long taskId, Long deviceId,
                                        String moduleName, String versionLabel, String keyword) {
        return listAdvanced(page, pageSize, buildFilterMap(
                taskId, deviceId, moduleName, versionLabel, keyword,
                null, null, null, null, null, null, null));
    }

    public Page<RecordingResource> listAdvanced(int page, int pageSize, Map<String, Object> filters) {
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1));
        return recordingRepository.findAll(buildSpec(filters), pageable);
    }

    public Map<String, Object> facets() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modules", recordingRepository.distinctModules());
        out.put("versions", recordingRepository.distinctVersions());
        out.put("operators", recordingRepository.distinctOperators());
        out.put("device_ids", recordingRepository.distinctDeviceIds());
        out.put("retain_days", retainDays());
        return out;
    }

    public List<Map<String, Object>> toViewList(List<RecordingResource> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RecordingResource r : rows) {
            list.add(toView(r));
        }
        return list;
    }

    public Map<String, Object> toView(RecordingResource r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("task_id", r.getTaskId());
        m.put("execution_id", r.getExecutionId());
        m.put("device_id", r.getDeviceId());
        m.put("file_name", r.getFileName());
        m.put("file_path", r.getFilePath());
        m.put("file_size", r.getFileSize());
        m.put("duration_seconds", r.getDurationSeconds());
        m.put("format", r.getFormat());
        m.put("watermark_hash", r.getWatermarkHash());
        m.put("watermark_payload", r.getWatermarkPayload());
        m.put("operator_label", r.getOperatorLabel());
        m.put("thumbnail_path", r.getThumbnailPath());
        m.put("module_name", r.getModuleName());
        m.put("version_label", r.getVersionLabel());
        m.put("project_code", r.getProjectCode());
        m.put("session_id", r.getSessionId());
        m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
        m.put("review_status", r.getReviewStatus());
        m.put("created_at", r.getCreatedAt());
        m.put("updated_at", r.getUpdatedAt());

        String display = resolveDisplayStatus(r);
        m.put("display_status", display);
        m.put("display_status_label", displayStatusLabel(display));
        long ageDays = ageDays(r);
        int retain = retainDays();
        long daysLeft = retain - ageDays;
        boolean archived = "archived".equalsIgnoreCase(blankToNull(r.getReviewStatus()));
        boolean expireSoon = !archived && daysLeft <= EXPIRE_WARN_DAYS;
        boolean expired = !archived && daysLeft <= 0;
        m.put("age_days", ageDays);
        m.put("days_left", daysLeft);
        m.put("expire_soon", expireSoon);
        m.put("expired", expired);
        m.put("retain_days", retain);
        return m;
    }

    @Transactional
    public Map<String, Object> archive(Long id) {
        RecordingResource rec = get(id);
        rec.setReviewStatus("archived");
        recordingRepository.save(rec);
        return toView(rec);
    }

    @Transactional
    public Map<String, Object> batchArchive(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new AppException("INVALID", "请选择要归档的录屏", HttpStatus.BAD_REQUEST);
        }
        int n = 0;
        for (Long id : ids) {
            var opt = recordingRepository.findById(id);
            if (opt.isPresent()) {
                RecordingResource rec = opt.get();
                rec.setReviewStatus("archived");
                recordingRepository.save(rec);
                n++;
            }
        }
        return Map.of("archived", n);
    }

    @Transactional
    public Map<String, Object> batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new AppException("INVALID", "请选择要删除的录屏", HttpStatus.BAD_REQUEST);
        }
        int n = 0;
        for (Long id : ids) {
            if (recordingRepository.existsById(id)) {
                delete(id);
                n++;
            }
        }
        return Map.of("deleted", n);
    }

    public void writeExportZip(List<Long> ids, OutputStream out) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new AppException("INVALID", "请选择要导出的录屏", HttpStatus.BAD_REQUEST);
        }
        LocalRecordingStorageService local = localStorageProvider.getIfAvailable();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            int added = 0;
            for (Long id : ids) {
                RecordingResource rec = recordingRepository.findById(id).orElse(null);
                if (rec == null || rec.getFilePath() == null || rec.getFilePath().isBlank()) {
                    continue;
                }
                Path file = resolveLocalFile(local, rec.getFilePath());
                if (file == null || !Files.isRegularFile(file)) {
                    continue;
                }
                String entryName = (rec.getFileName() != null && !rec.getFileName().isBlank())
                        ? rec.getId() + "_" + rec.getFileName()
                        : "recording_" + rec.getId() + ".mp4";
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zos);
                zos.closeEntry();
                added++;
            }
            if (added == 0) {
                throw new AppException("NOT_FOUND", "未找到可导出的本地录屏文件", HttpStatus.NOT_FOUND);
            }
        }
    }

    public String getPlaybackUrl(Long id) {
        RecordingResource rec = get(id);
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

    @Transactional
    public void delete(Long id) {
        RecordingResource rec = recordingRepository.findById(id).orElse(null);
        if (rec == null) {
            return;
        }
        deleteLocalFile(rec.getFilePath());
        deleteLocalFile(rec.getThumbnailPath());
        recordingRepository.delete(rec);
    }

    /** 录屏回放上下文：步骤 markers、标注、指标（来自关联 operation_record） */
    public Map<String, Object> getPlaybackContext(Long id) {
        RecordingResource rec = get(id);
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
        RecordingResource rec = get(id);
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

    private RecordingResource get(Long id) {
        return recordingRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "录屏不存在", HttpStatus.NOT_FOUND));
    }

    private int retainDays() {
        int d = properties.getStorage() != null ? properties.getStorage().getRetainDays() : DEFAULT_RETAIN_DAYS;
        return d > 0 ? d : DEFAULT_RETAIN_DAYS;
    }

    private long ageDays(RecordingResource r) {
        if (r.getCreatedAt() == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(r.getCreatedAt().toLocalDate(), LocalDate.now()));
    }

    private String resolveDisplayStatus(RecordingResource r) {
        String review = blankToNull(r.getReviewStatus());
        if ("archived".equalsIgnoreCase(review)) return "archived";
        if (!"archived".equalsIgnoreCase(review) && ageDays(r) >= retainDays()) return "expired";
        if ("pending_review".equalsIgnoreCase(review)) return "pending_review";
        if ("approved".equalsIgnoreCase(review)) return "approved";
        if (r.getStatus() == RecordingResource.RecordingStatus.ready) return "ready";
        if (r.getStatus() == RecordingResource.RecordingStatus.recording) return "recording";
        if (r.getStatus() == RecordingResource.RecordingStatus.uploading) return "uploading";
        if (r.getStatus() == RecordingResource.RecordingStatus.failed) return "failed";
        return r.getStatus() != null ? r.getStatus().name() : "unknown";
    }

    private String displayStatusLabel(String code) {
        return switch (code) {
            case "ready" -> "录制就绪";
            case "pending_review" -> "待审核";
            case "approved" -> "审核通过";
            case "archived" -> "已归档";
            case "expired" -> "已过期";
            case "recording" -> "录制中";
            case "uploading" -> "上传中";
            case "failed" -> "录制失败";
            default -> code;
        };
    }

    private Specification<RecordingResource> buildSpec(Map<String, Object> filters) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (filters == null) {
                query.orderBy(cb.desc(root.get("createdAt")));
                return cb.and(preds.toArray(new Predicate[0]));
            }
            Long taskId = asLong(filters.get("task_id"));
            Long deviceId = asLong(filters.get("device_id"));
            String moduleName = asStr(filters.get("module_name"));
            String versionLabel = asStr(filters.get("version_label"));
            String keyword = asStr(filters.get("keyword"));
            String operator = asStr(filters.get("operator_label"));
            String durationBucket = asStr(filters.get("duration_bucket"));
            String reviewStatus = asStr(filters.get("review_status"));
            String createdRange = asStr(filters.get("created_range"));
            LocalDateTime createdFrom = asDateTime(filters.get("created_from"));
            LocalDateTime createdTo = asDateTime(filters.get("created_to"));

            if (taskId != null) preds.add(cb.equal(root.get("taskId"), taskId));
            if (deviceId != null) preds.add(cb.equal(root.get("deviceId"), deviceId));
            if (moduleName != null) {
                preds.add(cb.equal(root.get("moduleName"), moduleName));
            }
            if (versionLabel != null) {
                preds.add(cb.equal(root.get("versionLabel"), versionLabel));
            }
            if (operator != null) {
                preds.add(cb.equal(root.get("operatorLabel"), operator));
            }
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                var empty = cb.literal("");
                List<Predicate> kw = new ArrayList<>();
                kw.add(cb.like(cb.lower(cb.coalesce(root.get("fileName"), empty)), like));
                kw.add(cb.like(cb.lower(cb.coalesce(root.get("operatorLabel"), empty)), like));
                kw.add(cb.like(cb.lower(cb.coalesce(root.get("moduleName"), empty)), like));
                kw.add(cb.like(cb.lower(cb.coalesce(root.get("projectCode"), empty)), like));
                kw.add(cb.like(cb.lower(cb.coalesce(root.get("versionLabel"), empty)), like));
                try {
                    Long kid = Long.parseLong(keyword);
                    kw.add(cb.equal(root.get("taskId"), kid));
                    kw.add(cb.equal(root.get("deviceId"), kid));
                    kw.add(cb.equal(root.get("id"), kid));
                } catch (NumberFormatException ignored) {
                }
                preds.add(cb.or(kw.toArray(new Predicate[0])));
            }
            if (durationBucket != null) {
                switch (durationBucket) {
                    case "short" -> preds.add(cb.lt(root.get("durationSeconds"), 60));
                    case "medium" -> preds.add(cb.and(
                            cb.ge(root.get("durationSeconds"), 60),
                            cb.lt(root.get("durationSeconds"), 300)));
                    case "long" -> preds.add(cb.ge(root.get("durationSeconds"), 300));
                    default -> { }
                }
            }
            if (createdFrom != null) preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            if (createdTo != null) preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            if (createdRange != null && createdFrom == null && createdTo == null) {
                LocalDate today = LocalDate.now();
                switch (createdRange) {
                    case "today" -> {
                        preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), today.atStartOfDay()));
                        preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), today.atTime(LocalTime.MAX)));
                    }
                    case "7d" -> preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                            today.minusDays(6).atStartOfDay()));
                    case "30d" -> preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                            today.minusDays(29).atStartOfDay()));
                    default -> { }
                }
            }
            if (reviewStatus != null) {
                int retain = retainDays();
                LocalDateTime expireBefore = LocalDate.now().minusDays(retain).atStartOfDay();
                switch (reviewStatus) {
                    case "pending_review" -> preds.add(cb.equal(root.get("reviewStatus"), "pending_review"));
                    case "approved" -> preds.add(cb.equal(root.get("reviewStatus"), "approved"));
                    case "archived" -> preds.add(cb.equal(root.get("reviewStatus"), "archived"));
                    case "ready" -> {
                        preds.add(cb.equal(root.get("status"), RecordingResource.RecordingStatus.ready));
                        preds.add(cb.or(
                                cb.isNull(root.get("reviewStatus")),
                                cb.equal(root.get("reviewStatus"), "")));
                        preds.add(cb.greaterThan(root.get("createdAt"), expireBefore));
                    }
                    case "expired" -> {
                        preds.add(cb.or(
                                cb.isNull(root.get("reviewStatus")),
                                cb.notEqual(root.get("reviewStatus"), "archived")));
                        preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), expireBefore));
                    }
                    default -> { }
                }
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Map<String, Object> buildFilterMap(Long taskId, Long deviceId, String moduleName,
                                               String versionLabel, String keyword,
                                               String operator, String durationBucket, String reviewStatus,
                                               String createdRange, LocalDateTime createdFrom, LocalDateTime createdTo,
                                               Object unused) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task_id", taskId);
        m.put("device_id", deviceId);
        m.put("module_name", moduleName);
        m.put("version_label", versionLabel);
        m.put("keyword", keyword);
        m.put("operator_label", operator);
        m.put("duration_bucket", durationBucket);
        m.put("review_status", reviewStatus);
        m.put("created_range", createdRange);
        m.put("created_from", createdFrom);
        m.put("created_to", createdTo);
        return m;
    }

    private Path resolveLocalFile(LocalRecordingStorageService local, String objectPath) {
        try {
            if (local != null) {
                return local.resolvePath(objectPath);
            }
            Path p = Path.of(objectPath);
            if (p.isAbsolute() && Files.exists(p)) return p;
            Path base = Path.of(properties.getStorage().getLocalPath()).toAbsolutePath().normalize();
            Path file = base.resolve(objectPath).normalize();
            if (file.startsWith(base) && Files.exists(file)) return file;
        } catch (Exception ignored) {
        }
        return null;
    }

    private void deleteLocalFile(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) return;
        try {
            Path file = resolveLocalFile(localStorageProvider.getIfAvailable(), objectPath);
            if (file != null) Files.deleteIfExists(file);
        } catch (Exception ignored) {
        }
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String asStr(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long asLong(Object v) {
        if (v == null || "".equals(v)) return null;
        try {
            return Long.valueOf(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime asDateTime(Object v) {
        if (v == null || "".equals(v)) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        try {
            String s = v.toString().trim();
            if (s.length() <= 10) return LocalDate.parse(s).atStartOfDay();
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
