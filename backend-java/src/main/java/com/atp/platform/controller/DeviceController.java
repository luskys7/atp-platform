package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.common.PageResult;
import com.atp.platform.dto.DeviceRegisterRequest;
import com.atp.platform.entity.Device;
import com.atp.platform.entity.DeviceWhitelist;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.AuthService;
import com.atp.platform.service.DeviceDiscoveryService;
import com.atp.platform.service.DeviceService;
import com.atp.platform.service.IosWdaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final AuthService authService;
    private final IosWdaService iosWdaService;
    private final DeviceDiscoveryService deviceDiscoveryService;

    @PostMapping("/api/v1/agent/devices/register")
    public ApiResponse<Device> register(@Valid @RequestBody DeviceRegisterRequest req) {
        return ApiResponse.ok(deviceService.register(req));
    }

    @PostMapping("/api/v1/agent/devices/heartbeat")
    public ApiResponse<Void> heartbeat(@RequestBody Map<String, Object> body) {
        String serial = (String) body.get("serial_number");
        Integer battery = body.get("battery_level") != null ? ((Number) body.get("battery_level")).intValue() : 0;
        deviceService.heartbeat(serial, battery);
        return ApiResponse.ok();
    }

    @GetMapping("/api/v1/devices")
    public ApiResponse<PageResult<Device>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String status) {
        Page<Device> result = deviceService.list(page, pageSize, platform, status);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/api/v1/devices/{id}")
    public ApiResponse<Device> get(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.getById(id));
    }

    @PutMapping("/api/v1/devices/{id}/status")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        deviceService.updateStatus(id, Device.DeviceStatus.valueOf(body.get("status")));
        authService.createAuditLog(SecurityUtils.currentUserId(), "update_status", "device", id.toString(),
                body.get("status"), request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/v1/devices/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        deviceService.delete(id);
        authService.createAuditLog(SecurityUtils.currentUserId(), "delete", "device", id.toString(), "", request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @GetMapping("/api/v1/devices/whitelist")
    public ApiResponse<PageResult<DeviceWhitelist>> listWhitelist(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        Page<DeviceWhitelist> result = deviceService.listWhitelist(page, pageSize);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @PostMapping("/api/v1/devices/whitelist")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<DeviceWhitelist> addWhitelist(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(deviceService.addWhitelist(
                body.get("serial_number"), body.get("platform"), body.get("remark"), SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/api/v1/devices/whitelist/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> removeWhitelist(@PathVariable Long id) {
        deviceService.removeWhitelist(id);
        return ApiResponse.ok();
    }

    @PutMapping("/api/v1/devices/{id}/calibration")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Device> updateCalibration(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String json = body.containsKey("calibration_json") ? body.get("calibration_json").toString()
                : "{\"offset_x\":0,\"offset_y\":0,\"scale_x\":1,\"scale_y\":1}";
        return ApiResponse.ok(deviceService.updateCalibration(id, json));
    }

    @PostMapping("/api/v1/devices/{id}/install")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, String>> installApp(@PathVariable Long id, @RequestBody Map<String, String> body) {
        deviceService.installApp(id, body.get("app_path"));
        return ApiResponse.ok(Map.of("message", "安装指令已下发"));
    }

    @PostMapping("/api/v1/devices/{id}/uninstall")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, String>> uninstallApp(@PathVariable Long id, @RequestBody Map<String, String> body) {
        deviceService.uninstallApp(id, body.get("app_package"));
        return ApiResponse.ok(Map.of("message", "卸载指令已下发"));
    }

    @PutMapping("/api/v1/devices/{id}/tags")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Device> updateTags(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(deviceService.updateTags(id, body.get("tags")));
    }

    @PostMapping("/api/v1/devices/{id}/reset-health")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> resetHealth(@PathVariable Long id, HttpServletRequest request) {
        deviceService.resetDeviceHealth(id);
        authService.createAuditLog(SecurityUtils.currentUserId(), "reset_health", "device", id.toString(), "", request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @PostMapping("/api/v1/devices/sync-usb")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> syncUsbDevices() {
        return ApiResponse.ok(deviceDiscoveryService.syncUsbDevices());
    }

    @GetMapping("/api/v1/devices/{id}/wda/status")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> wdaStatus(@PathVariable Long id) {
        return ApiResponse.ok(iosWdaService.status(id));
    }

    @PostMapping("/api/v1/devices/{id}/wda/deploy")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> deployWda(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Object> result = iosWdaService.deploy(id);
        authService.createAuditLog(SecurityUtils.currentUserId(), "wda_deploy", "device", id.toString(),
                String.valueOf(result.get("message")), request.getRemoteAddr());
        return ApiResponse.ok(result);
    }
}
