package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String action,
            @RequestParam(name = "resource_type", required = false) String resourceType,
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Map<String, String> filters = new HashMap<>();
        if (action != null) filters.put("action", action);
        if (resourceType != null) filters.put("resource_type", resourceType);
        if (userId != null) filters.put("user_id", userId);
        if (ip != null) filters.put("ip", ip);
        if (from != null) filters.put("from", from);
        if (to != null) filters.put("to", to);
        return ApiResponse.ok(service.list(page, pageSize, filters));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(service.stats());
    }

    @PostMapping("/archive")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> archive() {
        return ApiResponse.ok(service.archive(SecurityUtils.currentUserId()));
    }

    @GetMapping("/archives")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<List<Map<String, Object>>> archives() {
        return ApiResponse.ok(service.listArchives());
    }

    @PostMapping("/archives/verify")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        String filename = body.get("filename") != null ? body.get("filename").toString() : null;
        return ApiResponse.ok(service.verifyArchive(filename));
    }
}
