package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.GlobalParameter;
import com.atp.platform.entity.GlobalParameterLog;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.GlobalParameterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/global-parameters")
@RequiredArgsConstructor
public class GlobalParameterController {

    private final GlobalParameterService service;

    @GetMapping
    public ApiResponse<List<GlobalParameter>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<GlobalParameterLog>> logs(@PathVariable Long id) {
        return ApiResponse.ok(service.logs(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<GlobalParameter> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(body, SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<GlobalParameter> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body, SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<GlobalParameter> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(body.get("enabled").toString());
        return ApiResponse.ok(service.toggle(id, enabled, SecurityUtils.currentUserId()));
    }
}
