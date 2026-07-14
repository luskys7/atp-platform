package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupSchedulerService {

    private final BackupService backupService;
    private final AtpProperties properties;

    @Scheduled(cron = "${atp.backup.cron:0 0 2 * * *}")
    public void runDailyBackup() {
        if (!properties.getBackup().isAutoEnabled()) {
            return;
        }
        try {
            Map<String, Object> meta = backupService.create(null);
            log.info("Daily backup created: {}", meta.get("filename"));
            int pruned = backupService.pruneOldBackups(properties.getBackup().getRetainDays());
            if (pruned > 0) {
                log.info("Pruned {} old backup(s)", pruned);
            }
        } catch (Exception e) {
            log.error("Daily backup failed", e);
        }
    }
}
