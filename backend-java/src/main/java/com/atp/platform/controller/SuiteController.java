package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.TestSuite;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.SuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/suites")
@RequiredArgsConstructor
public class SuiteController {

    private final SuiteService service;

    @GetMapping
    public ApiResponse<List<TestSuite>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        return ApiResponse.ok(service.getDetail(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestSuite> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(body, SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestSuite> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> run(@PathVariable Long id) {
        return ApiResponse.ok(service.runSuite(id, SecurityUtils.currentUserId()));
    }

    @PostMapping("/runs/{runId}/restore-config")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestSuite> restoreConfig(@PathVariable Long runId) {
        return ApiResponse.ok(service.restoreFromRunSnapshot(runId));
    }
}
