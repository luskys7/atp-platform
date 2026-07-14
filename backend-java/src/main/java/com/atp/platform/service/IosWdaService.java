package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.Device;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IosWdaService {

    private final AtpProperties properties;
    private final PythonExecutorClient executorClient;
    private final DeviceRepository deviceRepository;

    public Map<String, Object> status(Long deviceId) {
        Device device = getDevice(deviceId);
        String host = device.getAgentHost() != null ? device.getAgentHost() : "127.0.0.1";
        int port = device.getWdaPort() != null ? device.getWdaPort() : properties.getIosWda().getWdaPort();
        Map<String, Object> health = executorClient.wdaStatus(host, port);
        Map<String, Object> result = new HashMap<>();
        result.put("device_id", deviceId);
        result.put("serial_number", device.getSerialNumber());
        result.put("agent_host", host);
        result.put("wda_port", port);
        result.put("health", health);
        result.put("ok", Boolean.TRUE.equals(health.get("ok")));
        return result;
    }

    public Map<String, Object> deploy(Long deviceId) {
        Device device = getDevice(deviceId);
        if (device.getPlatform() != Device.Platform.ios) {
            throw new AppException("INVALID", "仅 iOS 设备支持 WDA 部署", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> config = buildDeployConfig(device);
        Map<String, Object> result = executorClient.deployWda(device.getSerialNumber(), config);
        if (Boolean.TRUE.equals(result.get("success"))) {
            Object portObj = result.get("wda_port");
            if (portObj instanceof Number n) {
                device.setWdaPort(n.intValue());
                deviceRepository.save(device);
            }
            log.info("WDA deployed for device {} serial={}", deviceId, device.getSerialNumber());
        }
        result.put("device_id", deviceId);
        return result;
    }

    public void tryAutoDeploy(Device device) {
        if (!properties.getIosWda().isAutoDeployEnabled()) {
            return;
        }
        if (device.getPlatform() != Device.Platform.ios) {
            return;
        }
        String host = device.getAgentHost() != null ? device.getAgentHost() : "127.0.0.1";
        int port = device.getWdaPort() != null ? device.getWdaPort() : properties.getIosWda().getWdaPort();
        Map<String, Object> health = executorClient.wdaStatus(host, port);
        if (Boolean.TRUE.equals(health.get("ok"))) {
            return;
        }
        try {
            deploy(device.getId());
        } catch (Exception e) {
            log.warn("Auto WDA deploy skipped for {}: {}", device.getSerialNumber(), e.getMessage());
        }
    }

    private Map<String, Object> buildDeployConfig(Device device) {
        AtpProperties.IosWda cfg = properties.getIosWda();
        Map<String, Object> body = new HashMap<>();
        body.put("wda_ipa_path", cfg.getWdaIpaPath());
        body.put("bundle_id", cfg.getBundleId());
        body.put("wda_port", device.getWdaPort() != null ? device.getWdaPort() : cfg.getWdaPort());
        body.put("local_port", device.getWdaPort() != null ? device.getWdaPort() : cfg.getWdaPort());
        body.put("auto_install", true);
        body.put("team_id", cfg.getTeamId());
        body.put("p12_path", cfg.getP12Path());
        body.put("p12_password", cfg.getP12Password());
        body.put("provisioning_profile_path", cfg.getProvisioningProfilePath());
        return body;
    }

    private Device getDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "设备不存在", HttpStatus.NOT_FOUND));
    }
}
