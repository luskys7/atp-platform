package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.ManualIntervention;
import com.atp.platform.entity.PlatformMessage;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.ManualInterventionService;
import com.atp.platform.service.PlatformMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/interventions")
@RequiredArgsConstructor
public class ManualInterventionController {

    private final ManualInterventionService service;

    @GetMapping("/pending")
    public ApiResponse<List<ManualIntervention>> pending() {
        return ApiResponse.ok(service.listPending());
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<List<ManualIntervention>> byTask(@PathVariable Long taskId) {
        return ApiResponse.ok(service.listByTask(taskId));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String action = body.getOrDefault("action", "continue").toString();
        String note = body.get("note") != null ? body.get("note").toString() : "";
        return ApiResponse.ok(service.resolve(id, action, SecurityUtils.currentUserId(), note));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        service.cancel(id, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }
}
