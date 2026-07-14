package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageRetentionSchedulerService {

    private final AtpProperties properties;

    @Scheduled(cron = "${atp.storage.cleanup-cron:0 0 3 * * *}")
    public void cleanupExpiredFiles() {
        var storage = properties.getStorage();
        if (!storage.isAutoCleanupEnabled()) {
            return;
        }
        int days = storage.getRetainDays();
        if (days <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int deleted = 0;
        deleted += purgeDirectory(Path.of(storage.getLocalPath()), cutoff);
        deleted += purgeDirectory(Path.of(storage.getReportsPath()), cutoff);
        if (deleted > 0) {
            log.info("Storage retention cleanup: deleted {} file(s), retainDays={}", deleted, days);
        }
    }

    private int purgeDirectory(Path root, Instant cutoff) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int[] count = {0};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        try {
                            Files.deleteIfExists(file);
                            count[0]++;
                        } catch (IOException e) {
                            log.warn("Failed to delete {}", file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Storage cleanup walk failed for {}", root, e);
        }
        return count[0];
    }
}
