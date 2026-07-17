package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.dto.DeviceRegisterRequest;
import com.atp.platform.entity.Device;
import com.atp.platform.entity.DeviceWhitelist;
import com.atp.platform.repository.DeviceRepository;
import com.atp.platform.repository.DeviceWhitelistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDiscoveryService {

    public static final String AUTO_REMARK = "USB自动发现";

    private final AtpProperties properties;
    private final PythonExecutorClient executorClient;
    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final DeviceWhitelistRepository whitelistRepository;

    /** 从默认执行器（atp.executor.url）拉取本机 USB 设备 */
    @Transactional
    public Map<String, Object> syncUsbDevices() {
        String base = DeviceService.normalizeExecutorUrl(properties.getExecutor().getUrl());
        Map<String, Object> scan = executorClient.listUsbDevices(base);
        if (!Boolean.TRUE.equals(scan.get("success"))) {
            return Map.of(
                    "success", false,
                    "message", scan.getOrDefault("message", "adb 扫描失败"),
                    "synced", 0
            );
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> found = (List<Map<String, Object>>) scan.getOrDefault("devices", List.of());
        return applyScan(base, found);
    }

    /**
     * 远程执行器主动上报本机 USB 设备列表。
     * @param executorUrl 浏览器与中央后端均可访问的执行器基址，如 http://10.0.0.12:9002
     */
    @Transactional
    public Map<String, Object> ingestFromExecutor(String executorUrl, List<Map<String, Object>> devices) {
        String base = DeviceService.normalizeExecutorUrl(executorUrl);
        if (base == null || base.isBlank()) {
            return Map.of("success", false, "message", "executor_url 必填", "synced", 0);
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            return Map.of("success", false, "message", "executor_url 须为 http(s) 地址", "synced", 0);
        }
        List<Map<String, Object>> found = devices != null ? devices : List.of();
        return applyScan(base, found);
    }

    private Map<String, Object> applyScan(String executorUrl, List<Map<String, Object>> found) {
        Set<String> seen = new HashSet<>();
        int synced = 0;
        List<String> serials = new ArrayList<>();

        for (Map<String, Object> row : found) {
            String serial = str(row.get("serial_number"));
            if (serial.isBlank()) continue;
            seen.add(serial);
            ensureAutoWhitelist(serial);
            DeviceRegisterRequest req = toRegisterRequest(row, executorUrl);
            deviceService.register(req);
            synced++;
            serials.add(serial);
        }

        markUnpluggedOffline(seen, executorUrl);
        return Map.of(
                "success", true,
                "synced", synced,
                "serials", serials,
                "executor_url", executorUrl,
                "message", synced > 0
                        ? "已同步 " + synced + " 台 USB 设备 → " + executorUrl
                        : "未检测到 USB 真机（执行器 " + executorUrl + "）"
        );
    }

    private void ensureAutoWhitelist(String serial) {
        String remark = properties.getDevice().getUsbAutoWhitelistRemark();
        if (remark == null || remark.isBlank()) remark = AUTO_REMARK;
        if (whitelistRepository.findBySerialNumber(serial).isPresent()) {
            return;
        }
        DeviceWhitelist wl = new DeviceWhitelist();
        wl.setSerialNumber(serial);
        wl.setPlatform(Device.Platform.android);
        wl.setRemark(remark);
        wl.setCreatedBy(1L);
        whitelistRepository.save(wl);
        log.info("USB auto-whitelist: {}", serial);
    }

    /** 仅下线「归属本执行器」且已拔出的自动发现设备，避免误伤其他节点上的手机 */
    private void markUnpluggedOffline(Set<String> connected, String executorUrl) {
        String remark = properties.getDevice().getUsbAutoWhitelistRemark();
        if (remark == null || remark.isBlank()) remark = AUTO_REMARK;
        String normalized = DeviceService.normalizeExecutorUrl(executorUrl);
        for (DeviceWhitelist wl : whitelistRepository.findAll()) {
            if (!remark.equals(wl.getRemark())) continue;
            if (connected.contains(wl.getSerialNumber())) continue;
            deviceRepository.findBySerialNumber(wl.getSerialNumber()).ifPresent(d -> {
                if (!sameExecutor(d.getExecutorUrl(), normalized)) {
                    return;
                }
                if (d.getStatus() == Device.DeviceStatus.online || d.getStatus() == Device.DeviceStatus.busy) {
                    d.setStatus(Device.DeviceStatus.offline);
                    deviceRepository.save(d);
                }
            });
        }
    }

    private boolean sameExecutor(String deviceUrl, String scanUrl) {
        String a = DeviceService.normalizeExecutorUrl(deviceUrl);
        String b = DeviceService.normalizeExecutorUrl(scanUrl);
        if (b == null || b.isBlank()) return true;
        if (a == null || a.isBlank()) {
            // 兼容旧数据：未写 executor_url 的视为默认执行器
            String def = DeviceService.normalizeExecutorUrl(properties.getExecutor().getUrl());
            return Objects.equals(def, b);
        }
        return Objects.equals(a, b);
    }

    private DeviceRegisterRequest toRegisterRequest(Map<String, Object> row, String executorUrl) {
        DeviceRegisterRequest req = new DeviceRegisterRequest();
        req.setSerialNumber(str(row.get("serial_number")));
        req.setName(str(row.get("name")));
        req.setPlatform("android");
        req.setOsVersion(str(row.get("os_version")));
        req.setModel(str(row.get("model")));
        req.setAgentHost(str(row.get("agent_host")));
        req.setAgentPort(intVal(row.get("agent_port"), 9100));
        req.setExecutorUrl(executorUrl);
        req.setScreenWidth(intVal(row.get("screen_width"), null));
        req.setScreenHeight(intVal(row.get("screen_height"), null));
        req.setAdbPort(intVal(row.get("adb_port"), 5037));
        req.setBatteryLevel(intVal(row.get("battery_level"), 100));
        return req;
    }

    private String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private Integer intVal(Object o, Integer def) {
        if (o == null) return def;
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
