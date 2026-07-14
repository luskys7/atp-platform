package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.common.PageResult;
import com.atp.platform.entity.RecordingResource;
import com.atp.platform.entity.TestReport;
import com.atp.platform.service.RecordingService;
import com.atp.platform.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReportRecordingController {

    private final RecordingService recordingService;
    private final ReportService reportService;

    @GetMapping("/api/v1/recordings")
    public ApiResponse<PageResult<RecordingResource>> listRecordings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "task_id", required = false) Long taskId,
            @RequestParam(name = "device_id", required = false) Long deviceId,
            @RequestParam(name = "module_name", required = false) String moduleName,
            @RequestParam(name = "version_label", required = false) String versionLabel,
            @RequestParam(name = "keyword", required = false) String keyword) {
        Page<RecordingResource> result = recordingService.list(
                page, pageSize, taskId, deviceId, moduleName, versionLabel, keyword);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
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

    @DeleteMapping("/api/v1/recordings/{id}")
    public ApiResponse<Void> deleteRecording(@PathVariable Long id) {
        recordingService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/api/v1/reports")
    public ApiResponse<PageResult<TestReport>> listReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        Page<TestReport> result = reportService.list(page, pageSize);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/api/v1/reports/{taskId}")
    public ApiResponse<Map<String, Object>> reportDetail(@PathVariable Long taskId) {
        return ApiResponse.ok(reportService.getDetail(taskId));
    }

    @GetMapping("/api/v1/reports/{taskId}/export-info")
    public ApiResponse<Map<String, String>> exportPdfInfo(@PathVariable Long taskId) {
        return ApiResponse.ok(Map.of("pdf_path", reportService.exportPdf(taskId)));
    }
}
