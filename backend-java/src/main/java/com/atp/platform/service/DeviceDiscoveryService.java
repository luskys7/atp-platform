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

import java.time.LocalDateTime;
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

    @Transactional
    public Map<String, Object> syncUsbDevices() {
        Map<String, Object> scan = executorClient.listUsbDevices();
        if (!Boolean.TRUE.equals(scan.get("success"))) {
            return Map.of(
                    "success", false,
                    "message", scan.getOrDefault("message", "adb 扫描失败"),
                    "synced", 0
            );
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> found = (List<Map<String, Object>>) scan.getOrDefault("devices", List.of());
        Set<String> seen = new HashSet<>();
        int synced = 0;
        List<String> serials = new ArrayList<>();

        for (Map<String, Object> row : found) {
            String serial = str(row.get("serial_number"));
            if (serial.isBlank()) continue;
            seen.add(serial);
            ensureAutoWhitelist(serial);
            deviceService.register(toRegisterRequest(row));
            synced++;
            serials.add(serial);
        }

        markUnpluggedOffline(seen);
        return Map.of(
                "success", true,
                "synced", synced,
                "serials", serials,
                "message", synced > 0 ? "已同步 " + synced + " 台 USB 设备" : "未检测到 USB 真机，请确认 adb devices 可见"
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

    private void markUnpluggedOffline(Set<String> connected) {
        String remark = properties.getDevice().getUsbAutoWhitelistRemark();
        if (remark == null || remark.isBlank()) remark = AUTO_REMARK;
        for (DeviceWhitelist wl : whitelistRepository.findAll()) {
            if (!remark.equals(wl.getRemark())) continue;
            if (connected.contains(wl.getSerialNumber())) continue;
            deviceRepository.findBySerialNumber(wl.getSerialNumber()).ifPresent(d -> {
                if (d.getStatus() == Device.DeviceStatus.online || d.getStatus() == Device.DeviceStatus.busy) {
                    d.setStatus(Device.DeviceStatus.offline);
                    deviceRepository.save(d);
                }
            });
        }
    }

    private DeviceRegisterRequest toRegisterRequest(Map<String, Object> row) {
        DeviceRegisterRequest req = new DeviceRegisterRequest();
        req.setSerialNumber(str(row.get("serial_number")));
        req.setName(str(row.get("name")));
        req.setPlatform("android");
        req.setOsVersion(str(row.get("os_version")));
        req.setModel(str(row.get("model")));
        req.setAgentHost(str(row.get("agent_host")));
        req.setAgentPort(intVal(row.get("agent_port"), 9100));
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
