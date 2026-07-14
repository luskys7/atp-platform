package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.CommonStep;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.CommonStepService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/common-steps")
@RequiredArgsConstructor
public class CommonStepController {

    private final CommonStepService service;

    @GetMapping
    public ApiResponse<List<CommonStep>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<CommonStep> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<CommonStep> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.create(body, SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<CommonStep> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> transfer(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<?>) body.get("ids")).stream()
                .map(o -> Long.valueOf(o.toString()))
                .toList();
        Long ownerId = Long.valueOf(body.get("owner_id").toString());
        return ApiResponse.ok(service.transferSteps(ids, ownerId));
    }

    @GetMapping("/{id}/dependencies")
    public ApiResponse<Map<String, Object>> dependencies(@PathVariable Long id) {
        return ApiResponse.ok(service.checkDependencies(id));
    }
}
