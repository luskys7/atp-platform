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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
            @RequestHeader(value = "X-ATP-Webhook-Token", required = false) String token,
            HttpServletRequest request) {
        if (token == null) {
            token = request.getParameter("token");
        }
        return ApiResponse.ok(ciService.handleJenkinsWebhook(req, token));
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
}
