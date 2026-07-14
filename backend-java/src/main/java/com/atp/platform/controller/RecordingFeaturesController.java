package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.RecordingConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/recording")
@RequiredArgsConstructor
public class RecordingFeaturesController {

    private final RecordingConfigService recordingConfigService;

    @GetMapping("/features")
    public ApiResponse<Map<String, Object>> features() {
        return ApiResponse.ok(recordingConfigService.getFeatures());
    }

    @PutMapping("/features")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> updateFeatures(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(recordingConfigService.updateFeatures(body, SecurityUtils.currentUserId()));
    }

    @PostMapping("/features/reset")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> resetFeatures() {
        return ApiResponse.ok(recordingConfigService.resetToYamlDefaults(SecurityUtils.currentUserId()));
    }
}
