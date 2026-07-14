package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.OperationRecord;
import com.atp.platform.entity.TestCase;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.OperationRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operation-records")
@RequiredArgsConstructor
public class OperationRecordController {

    private static final String ID = "{id:\\d+}";

    private final OperationRecordService service;

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> start(@RequestBody Map<String, Object> body) {
        Long deviceId = Long.valueOf(body.get("device_id").toString());
        return ApiResponse.ok(service.start(deviceId, SecurityUtils.currentUserId(), body));
    }

    @PostMapping("/" + ID + "/events")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> append(@PathVariable Long id, @RequestBody Map<String, Object> event) {
        return ApiResponse.ok(service.appendEvent(id, event));
    }

    @PostMapping("/" + ID + "/last-click")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> patchLastClick(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.patchLastClickStep(id, body));
    }

    @PostMapping("/" + ID + "/steps/{stepIndex}/locator")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> patchStepLocator(
            @PathVariable Long id,
            @PathVariable int stepIndex,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.patchStepLocator(id, stepIndex, body));
    }

    @PostMapping("/" + ID + "/pause")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> pause(@PathVariable Long id) {
        return ApiResponse.ok(service.pause(id));
    }

    @PostMapping("/" + ID + "/resume")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> resume(@PathVariable Long id) {
        return ApiResponse.ok(service.resume(id));
    }

    @PostMapping("/" + ID + "/segments")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> markSegment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String label = body.getOrDefault("label", "").toString();
        return ApiResponse.ok(service.markSegment(id, label));
    }

    @PostMapping("/" + ID + "/finish")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> finish(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean denoise) {
        return ApiResponse.ok(service.finish(id, denoise));
    }

    @PostMapping("/" + ID + "/cancel")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }

    @GetMapping
    public ApiResponse<com.atp.platform.common.PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(page, pageSize, status));
    }

    @GetMapping("/audit-summary")
    public ApiResponse<Map<String, Object>> auditSummary(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(service.auditSummary(limit));
    }

    @GetMapping("/" + ID)
    public ApiResponse<OperationRecord> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/" + ID + "/validate-locators")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> validateLocators(@PathVariable Long id) {
        return ApiResponse.ok(service.validateStepLocators(id));
    }

    @GetMapping("/" + ID + "/preview-denoise")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> previewDenoise(@PathVariable Long id) {
        return ApiResponse.ok(service.previewDenoise(id));
    }

    @PutMapping("/" + ID + "/steps")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> updateSteps(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateSteps(id, body));
    }

    @PostMapping("/" + ID + "/emergency-save")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<OperationRecord> emergencySave(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = body.getOrDefault("reason", "disconnect").toString();
        return ApiResponse.ok(service.emergencySave(id, reason));
    }

    @PostMapping("/" + ID + "/inspect")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> inspect(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int x = body.get("x") != null ? Integer.parseInt(body.get("x").toString()) : 0;
        int y = body.get("y") != null ? Integer.parseInt(body.get("y").toString()) : 0;
        Integer displayWidth = body.get("display_width") != null
                ? Integer.parseInt(body.get("display_width").toString()) : null;
        Integer displayHeight = body.get("display_height") != null
                ? Integer.parseInt(body.get("display_height").toString()) : null;
        boolean blocking = Boolean.parseBoolean(String.valueOf(body.getOrDefault("blocking", false)));
        return ApiResponse.ok(service.inspectDuringRecording(id, x, y, displayWidth, displayHeight, blocking));
    }

    @PostMapping("/" + ID + "/warm-inspect")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> warmInspect(@PathVariable Long id) {
        service.warmInspectCache(id);
        return ApiResponse.ok(Map.of("ok", true));
    }

    @PostMapping("/" + ID + "/video")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> uploadVideo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "duration_seconds", required = false) Integer durationSeconds,
            @RequestParam(name = "operator_label", required = false) String operatorLabel,
            @RequestParam(name = "module_name", required = false) String moduleName,
            @RequestParam(name = "version_label", required = false) String versionLabel,
            @RequestParam(name = "project_code", required = false) String projectCode,
            @RequestParam(name = "crop_rect_json", required = false) String cropRectJson,
            @RequestParam(name = "client_metrics_json", required = false) String clientMetricsJson,
            @RequestParam(name = "thumbnail", required = false) MultipartFile thumbnail) {
        return ApiResponse.ok(service.uploadVideo(
                id, file, durationSeconds, operatorLabel, SecurityUtils.currentUserId(), thumbnail,
                moduleName, versionLabel, projectCode, cropRectJson, clientMetricsJson));
    }

    @PostMapping("/" + ID + "/to-case")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestCase> toCase(@PathVariable Long id, @RequestBody Map<String, Object> meta) {
        return ApiResponse.ok(service.toCase(id, meta, SecurityUtils.currentUserId()));
    }

    @PostMapping("/" + ID + "/to-cases-by-segments")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<List<TestCase>> toCasesBySegments(@PathVariable Long id, @RequestBody Map<String, Object> meta) {
        return ApiResponse.ok(service.toCasesBySegments(id, meta, SecurityUtils.currentUserId()));
    }

    @PostMapping("/" + ID + "/to-automation-case")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestCase> toAutomationCase(@PathVariable Long id, @RequestBody Map<String, Object> meta) {
        return ApiResponse.ok(service.toAutomationCase(id, meta, SecurityUtils.currentUserId()));
    }
}
