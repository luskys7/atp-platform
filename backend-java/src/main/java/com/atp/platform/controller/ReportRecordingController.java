package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.common.PageResult;
import com.atp.platform.entity.RecordingResource;
import com.atp.platform.entity.TestReport;
import com.atp.platform.service.RecordingService;
import com.atp.platform.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReportRecordingController {

    private final RecordingService recordingService;
    private final ReportService reportService;

    @GetMapping("/api/v1/recordings")
    public ApiResponse<PageResult<Map<String, Object>>> listRecordings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "task_id", required = false) Long taskId,
            @RequestParam(name = "device_id", required = false) Long deviceId,
            @RequestParam(name = "module_name", required = false) String moduleName,
            @RequestParam(name = "version_label", required = false) String versionLabel,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "operator_label", required = false) String operatorLabel,
            @RequestParam(name = "duration_bucket", required = false) String durationBucket,
            @RequestParam(name = "review_status", required = false) String reviewStatus,
            @RequestParam(name = "created_range", required = false) String createdRange,
            @RequestParam(name = "created_from", required = false) String createdFrom,
            @RequestParam(name = "created_to", required = false) String createdTo) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("task_id", taskId);
        filters.put("device_id", deviceId);
        filters.put("module_name", moduleName);
        filters.put("version_label", versionLabel);
        filters.put("keyword", keyword);
        filters.put("operator_label", operatorLabel);
        filters.put("duration_bucket", durationBucket);
        filters.put("review_status", reviewStatus);
        filters.put("created_range", createdRange);
        filters.put("created_from", createdFrom);
        filters.put("created_to", createdTo);
        Page<RecordingResource> result = recordingService.listAdvanced(page, pageSize, filters);
        return ApiResponse.ok(new PageResult<>(
                recordingService.toViewList(result.getContent()),
                result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/api/v1/recordings/facets")
    public ApiResponse<Map<String, Object>> facets() {
        return ApiResponse.ok(recordingService.facets());
    }

    @GetMapping("/api/v1/recordings/{id}/playback")
    public ApiResponse<Map<String, String>> playback(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("url", recordingService.getPlaybackUrl(id)));
    }

    @GetMapping("/api/v1/recordings/{id}/context")
    public ApiResponse<Map<String, Object>> playbackContext(@PathVariable Long id) {
        return ApiResponse.ok(recordingService.getPlaybackContext(id));
    }

    @GetMapping("/api/v1/recordings/{id}/watermark-verify")
    public ApiResponse<Map<String, Object>> verifyWatermark(@PathVariable Long id) {
        return ApiResponse.ok(recordingService.verifyWatermark(id));
    }

    @PostMapping("/api/v1/recordings/{id}/archive")
    public ApiResponse<Map<String, Object>> archive(@PathVariable Long id) {
        return ApiResponse.ok(recordingService.archive(id));
    }

    @PostMapping("/api/v1/recordings/batch-archive")
    public ApiResponse<Map<String, Object>> batchArchive(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(recordingService.batchArchive(asIdList(body.get("ids"))));
    }

    @PostMapping("/api/v1/recordings/batch-delete")
    public ApiResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(recordingService.batchDelete(asIdList(body.get("ids"))));
    }

    @PostMapping("/api/v1/recordings/batch-export")
    public ResponseEntity<StreamingResponseBody> batchExport(@RequestBody Map<String, Object> body) {
        List<Long> ids = asIdList(body.get("ids"));
        String filename = "recordings_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip";
        StreamingResponseBody stream = out -> recordingService.writeExportZip(ids, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }

    @DeleteMapping("/api/v1/recordings/{id}")
    public ApiResponse<Void> deleteRecording(@PathVariable Long id) {
        recordingService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/api/v1/reports")
    public ApiResponse<PageResult<Map<String, Object>>> listReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "module_name", required = false) String moduleName,
            @RequestParam(name = "version_label", required = false) String versionLabel,
            @RequestParam(name = "task_id", required = false) Long taskId,
            @RequestParam(name = "pass_bucket", required = false) String passBucket,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "created_range", required = false) String createdRange,
            @RequestParam(name = "created_from", required = false) String createdFrom,
            @RequestParam(name = "created_to", required = false) String createdTo) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("keyword", keyword);
        filters.put("module_name", moduleName);
        filters.put("version_label", versionLabel);
        filters.put("task_id", taskId);
        filters.put("pass_bucket", passBucket);
        filters.put("status", status);
        filters.put("created_range", createdRange);
        filters.put("created_from", createdFrom);
        filters.put("created_to", createdTo);
        Page<TestReport> result = reportService.listAdvanced(page, pageSize, filters);
        return ApiResponse.ok(new PageResult<>(
                reportService.toViewList(result.getContent()),
                result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/api/v1/reports/facets")
    public ApiResponse<Map<String, Object>> reportFacets() {
        return ApiResponse.ok(reportService.facets());
    }

    @GetMapping("/api/v1/reports/stats")
    public ApiResponse<Map<String, Object>> reportStats(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "module_name", required = false) String moduleName,
            @RequestParam(name = "version_label", required = false) String versionLabel,
            @RequestParam(name = "task_id", required = false) Long taskId,
            @RequestParam(name = "pass_bucket", required = false) String passBucket,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "created_range", required = false) String createdRange,
            @RequestParam(name = "created_from", required = false) String createdFrom,
            @RequestParam(name = "created_to", required = false) String createdTo) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("keyword", keyword);
        filters.put("module_name", moduleName);
        filters.put("version_label", versionLabel);
        filters.put("task_id", taskId);
        filters.put("pass_bucket", passBucket);
        filters.put("status", status);
        filters.put("created_range", createdRange);
        filters.put("created_from", createdFrom);
        filters.put("created_to", createdTo);
        return ApiResponse.ok(reportService.stats(filters));
    }

    @GetMapping("/api/v1/reports/{taskId}")
    public ApiResponse<Map<String, Object>> reportDetail(@PathVariable Long taskId) {
        return ApiResponse.ok(reportService.getDetail(taskId));
    }

    @PostMapping("/api/v1/reports/{id}/archive")
    public ApiResponse<Map<String, Object>> archiveReport(@PathVariable Long id) {
        return ApiResponse.ok(reportService.archive(id));
    }

    @DeleteMapping("/api/v1/reports/{id}")
    public ApiResponse<Map<String, Object>> deleteReport(@PathVariable Long id) {
        return ApiResponse.ok(reportService.delete(id));
    }

    @PostMapping("/api/v1/reports/batch-archive")
    public ApiResponse<Map<String, Object>> batchArchiveReports(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(reportService.batchArchive(asIdList(body.get("ids"))));
    }

    @PostMapping("/api/v1/reports/batch-delete")
    public ApiResponse<Map<String, Object>> batchDeleteReports(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(reportService.batchDelete(asIdList(body.get("ids"))));
    }

    @PostMapping("/api/v1/reports/purge-expired")
    public ApiResponse<Map<String, Object>> purgeExpiredReports(@RequestBody(required = false) Map<String, Object> body) {
        List<Long> ids = body != null ? asIdList(body.get("ids")) : List.of();
        return ApiResponse.ok(reportService.purgeExpired(ids));
    }

    @PostMapping("/api/v1/reports/batch-export")
    public ResponseEntity<StreamingResponseBody> batchExportReports(@RequestBody Map<String, Object> body) {
        List<Long> ids = asIdList(body.get("ids"));
        String filename = "reports_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip";
        StreamingResponseBody stream = out -> reportService.writeBatchExportZip(ids, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }

    @GetMapping("/api/v1/reports/{taskId}/export-info")
    public ApiResponse<Map<String, String>> exportPdfInfo(@PathVariable Long taskId) {
        return ApiResponse.ok(Map.of("pdf_path", reportService.exportPdf(taskId)));
    }

    @SuppressWarnings("unchecked")
    private List<Long> asIdList(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            return list.stream().map(v -> {
                if (v instanceof Number n) return n.longValue();
                return Long.parseLong(v.toString());
            }).toList();
        }
        return List.of();
    }
}
