package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDiscoveryScheduler {

    private final AtpProperties properties;
    private final DeviceDiscoveryService discoveryService;

    @Scheduled(fixedDelayString = "${atp.device.usb-scan-interval-ms:10000}")
    public void scanUsbDevices() {
        if (!properties.getDevice().isUsbAutoDiscoverEnabled()) {
            return;
        }
        try {
            var result = discoveryService.syncUsbDevices();
            int synced = (int) result.getOrDefault("synced", 0);
            if (synced > 0) {
                log.debug("USB device scan synced {} device(s)", synced);
            }
        } catch (Exception e) {
            log.warn("USB device scan failed: {}", e.getMessage());
        }
    }
}
