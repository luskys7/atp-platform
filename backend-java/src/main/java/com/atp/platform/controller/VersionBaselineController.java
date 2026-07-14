package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.VersionBaseline;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.VersionBaselineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/version-baselines")
@RequiredArgsConstructor
public class VersionBaselineController {

    private final VersionBaselineService service;

    @GetMapping
    public ApiResponse<List<VersionBaseline>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<VersionBaseline> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<VersionBaseline> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(body, SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<VersionBaseline> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        service.archive(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/compare")
    public ApiResponse<Map<String, Object>> compare(@PathVariable Long id) {
        return ApiResponse.ok(service.compareWithCurrent(id));
    }
}
