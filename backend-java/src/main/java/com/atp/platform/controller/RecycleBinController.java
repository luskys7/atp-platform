package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.RecycleBinItem;
import com.atp.platform.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinService service;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(service.stats());
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> preview(@PathVariable Long id) {
        return ApiResponse.ok(service.preview(id));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<RecycleBinItem> restore(@PathVariable Long id) {
        return ApiResponse.ok(service.restore(id));
    }

    @PostMapping("/batch-restore")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> batchRestore(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<?>) body.get("ids")).stream()
                .map(o -> Long.valueOf(o.toString()))
                .toList();
        return ApiResponse.ok(service.batchRestore(ids));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> purge(@PathVariable Long id) {
        service.purge(id);
        return ApiResponse.ok();
    }

    @PostMapping("/batch-purge")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> batchPurge(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<?>) body.get("ids")).stream()
                .map(o -> Long.valueOf(o.toString()))
                .toList();
        return ApiResponse.ok(service.batchPurge(ids));
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> clearAll() {
        return ApiResponse.ok(service.clearAll());
    }
}
