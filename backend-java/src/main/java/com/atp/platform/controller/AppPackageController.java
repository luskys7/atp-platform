package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.AppPackage;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.AppPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/app-packages")
@RequiredArgsConstructor
public class AppPackageController {

    private final AppPackageService service;

    @GetMapping
    public ApiResponse<List<AppPackage>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<AppPackage> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<AppPackage> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(name = "package_name", required = false) String packageName,
            @RequestParam(name = "version_name", required = false) String versionName,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "android") String platform,
            @RequestParam(name = "package_channel", defaultValue = "test") String packageChannel) throws Exception {
        return ApiResponse.ok(service.upload(file, name, packageName, versionName, branch, platform, packageChannel,
                SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/batch-install")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> batchInstall(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> deviceIds = ((List<Number>) body.get("device_ids")).stream().map(Number::longValue).toList();
        return ApiResponse.ok(service.batchInstall(id, deviceIds));
    }
}
