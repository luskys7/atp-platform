package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.DataFactoryTemplate;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.DataFactoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-factory")
@RequiredArgsConstructor
public class DataFactoryController {

    private final DataFactoryService service;

    @GetMapping("/templates")
    public ApiResponse<List<DataFactoryTemplate>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/templates/{id}")
    public ApiResponse<DataFactoryTemplate> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<DataFactoryTemplate> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(body, SecurityUtils.currentUserId()));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<DataFactoryTemplate> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @PostMapping("/cleanup/task/{taskId}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> cleanupTask(@PathVariable Long taskId) {
        int n = service.cleanupForTask(taskId);
        return ApiResponse.ok(Map.of("cleaned", n));
    }
}
