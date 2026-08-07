package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.dto.JenkinsWebhookRequest;
import com.atp.platform.dto.UpdateCiConfigRequest;
import com.atp.platform.entity.CiConfig;
import com.atp.platform.entity.CiJobRecord;
import com.atp.platform.service.CiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ci")
@RequiredArgsConstructor
public class CiController {

    private final CiService ciService;

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<CiConfig> getConfig() {
        return ApiResponse.ok(ciService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<CiConfig> updateConfig(@Valid @RequestBody UpdateCiConfigRequest req) {
        return ApiResponse.ok(ciService.updateConfig(req));
    }

    @PostMapping("/jenkins/webhook")
    public ApiResponse<Map<String, Object>> jenkinsWebhook(
            @RequestBody JenkinsWebhookRequest req,
            @RequestHeader(value = "X-ATP-Webhook-Token", required = false) String xToken,
            @RequestHeader(value = "Token", required = false) String tokenHeader,
            HttpServletRequest request) {
        String token = firstNonBlank(xToken, tokenHeader, request.getParameter("token"));
        return ApiResponse.ok(ciService.handleJenkinsWebhook(req, token, clientIp(request)));
    }

    @GetMapping("/tasks/{taskId}/status")
    public ApiResponse<Map<String, Object>> taskStatus(@PathVariable Long taskId) {
        return ApiResponse.ok(ciService.getTaskStatus(taskId));
    }

    @GetMapping("/jobs/recent")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<List<CiJobRecord>> recentJobs() {
        return ApiResponse.ok(ciService.recentJobs());
    }

    @GetMapping("/jobs/export")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ResponseEntity<byte[]> exportJobs() {
        List<CiJobRecord> jobs = ciService.allJobs();
        StringBuilder sb = new StringBuilder();
        sb.append("id,job_name,build_number,branch,task_id,suite_run_id,status,download_url,error_message,created_at\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (CiJobRecord j : jobs) {
            sb.append(csv(j.getId())).append(',')
                    .append(csv(j.getJobName())).append(',')
                    .append(csv(j.getBuildNumber())).append(',')
                    .append(csv(j.getBranch())).append(',')
                    .append(csv(j.getTaskId())).append(',')
                    .append(csv(j.getSuiteRunId())).append(',')
                    .append(csv(j.getStatus())).append(',')
                    .append(csv(j.getDownloadUrl())).append(',')
                    .append(csv(j.getErrorMessage())).append(',')
                    .append(csv(j.getCreatedAt() != null ? j.getCreatedAt().format(fmt) : "")).append('\n');
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ci-trigger-logs.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    private static String csv(Object v) {
        if (v == null) return "";
        String s = v.toString().replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s + "\"";
        }
        return s;
    }
}
