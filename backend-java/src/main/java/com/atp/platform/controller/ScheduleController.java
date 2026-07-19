package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.ScheduledJob;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService service;

    @GetMapping
    public ApiResponse<List<ScheduledJob>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping("/cron-preview")
    public ApiResponse<Map<String, Object>> cronPreview(@RequestBody Map<String, String> body) {
        String expression = body != null ? body.get("expression") : null;
        return ApiResponse.ok(service.previewCron(expression));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<ScheduledJob> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(body, SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<ScheduledJob> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<ScheduledJob> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.toggle(id, Boolean.parseBoolean(body.get("enabled").toString())));
    }
}
