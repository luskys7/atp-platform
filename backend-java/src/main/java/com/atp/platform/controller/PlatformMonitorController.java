package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.service.ExecutorPoolService;
import com.atp.platform.service.PlatformMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformMonitorController {

    private final PlatformMonitorService monitorService;
    private final ExecutorPoolService executorPoolService;

    @GetMapping("/monitor")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> monitor() {
        return ApiResponse.ok(monitorService.snapshot());
    }

    @GetMapping("/executor-events")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<List<Map<String, Object>>> executorEvents() {
        return ApiResponse.ok(executorPoolService.recentFailoverEvents());
    }

    @DeleteMapping("/executor-events")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> clearExecutorEvents() {
        executorPoolService.clearFailoverEvents();
        return ApiResponse.ok();
    }
}
