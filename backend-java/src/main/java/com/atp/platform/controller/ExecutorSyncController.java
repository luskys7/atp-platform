package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.service.DeviceDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 远程执行器主动上报本机 USB 设备（访客启动器 / platform_sync）。
 */
@RestController
@RequiredArgsConstructor
public class ExecutorSyncController {

    private final DeviceDiscoveryService deviceDiscoveryService;

    @PostMapping("/api/v1/executor/sync-devices")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> syncDevices(@RequestBody Map<String, Object> body) {
        String executorUrl = body.get("executor_url") != null ? body.get("executor_url").toString() : "";
        Object raw = body.get("devices");
        List<Map<String, Object>> devices = Collections.emptyList();
        if (raw instanceof List<?> list) {
            devices = (List<Map<String, Object>>) list;
        }
        return ApiResponse.ok(deviceDiscoveryService.ingestFromExecutor(executorUrl, devices));
    }
}
