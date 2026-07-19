package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.Device;
import com.atp.platform.entity.TestTask;
import com.atp.platform.repository.DeviceRepository;
import com.atp.platform.repository.TestTaskRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformMonitorService {

    private final AtpProperties properties;
    private final DeviceRepository deviceRepository;
    private final TestTaskRepository taskRepository;
    private final TaskQueueService taskQueue;
    private final ExecutorPoolService executorPoolService;
    private final EntityManager entityManager;
    private final RestClient restClient = RestClient.create();

    public Map<String, Object> snapshot() {
        Map<String, Object> db = checkDatabase();
        Map<String, Object> executor = checkExecutor();
        Map<String, Object> executorPool = executorPoolService.snapshot();
        Map<String, Object> storage = checkStorage();
        Map<String, Object> devices = deviceStats();
        Map<String, Object> scheduler = schedulerStats();

        String overall = "healthy";
        if (!"up".equals(db.get("status")) || !"up".equals(executor.get("status"))) {
            overall = "degraded";
        }
        if ("down".equals(db.get("status")) && "down".equals(executor.get("status"))) {
            overall = "critical";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checked_at", LocalDateTime.now().toString());
        result.put("overall", overall);
        result.put("backend", Map.of("status", "up", "service", "atp-platform-java"));
        result.put("database", db);
        result.put("executor", executor);
        result.put("executor_pool", executorPool);
        result.put("storage", storage);
        result.put("devices", devices);
        result.put("scheduler", scheduler);
        return result;
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> row = new LinkedHashMap<>();
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            row.put("status", "up");
            row.put("type", "h2/local");
        } catch (Exception e) {
            row.put("status", "down");
            row.put("error", e.getMessage());
        }
        return row;
    }

    private Map<String, Object> checkExecutor() {
        Map<String, Object> row = new LinkedHashMap<>();
        String base = properties.getExecutor().getUrl();
        String url = base + "/health";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);
            row.put("status", "up");
            row.put("url", base);
            if (body != null) {
                row.put("service", body.get("service"));
            }
            executorPoolService.onNodeHealthy(base);
        } catch (Exception e) {
            row.put("status", "down");
            row.put("url", base);
            row.put("error", e.getMessage());
            executorPoolService.onNodeUnhealthy(base, e.getMessage());
        }
        return row;
    }

    private Map<String, Object> checkStorage() {
        Map<String, Object> row = new LinkedHashMap<>();
        try {
            Path recordings = Paths.get(properties.getStorage().getLocalPath());
            Path reports = Paths.get(properties.getStorage().getReportsPath());
            long recBytes = dirSize(recordings);
            long repBytes = dirSize(reports);
            long totalBytes = recBytes + repBytes;
            long threshold = properties.getStorage().getWarnBytesThreshold();
            row.put("status", "up");
            row.put("recordings_path", recordings.toString());
            row.put("recordings_bytes", recBytes);
            row.put("reports_bytes", repBytes);
            row.put("total_bytes", totalBytes);
            row.put("disk_usage_percent", diskUsagePercent(recordings));
            row.put("disk_warn_threshold", 80);
            row.put("disk_critical_threshold", 90);
            if (threshold > 0) {
                row.put("warn_threshold_bytes", threshold);
                row.put("usage_percent", Math.min(100.0, totalBytes * 100.0 / threshold));
            }
            Double diskPct = (Double) row.get("disk_usage_percent");
            if (diskPct != null && diskPct >= 90) {
                row.put("status", "warn");
                row.put("alert", "磁盘使用率≥90%，请及时清理录屏与报告文件");
            } else if (diskPct != null && diskPct >= 80) {
                row.put("status", "warn");
                row.put("alert", "磁盘使用率≥80%，触发橙色预警");
            } else if (threshold > 0 && totalBytes >= threshold) {
                row.put("status", "warn");
                row.put("alert", "存储用量已超过告警阈值");
            } else if (threshold > 0 && totalBytes >= threshold * 0.85) {
                row.put("status", "warn");
                row.put("alert", "存储用量接近告警阈值");
            }
        } catch (Exception e) {
            row.put("status", "warn");
            row.put("error", e.getMessage());
        }
        return row;
    }

    private Double diskUsagePercent(Path path) {
        try {
            Path probe = Files.exists(path) ? path : Paths.get(".").toAbsolutePath();
            var store = Files.getFileStore(probe);
            long total = store.getTotalSpace();
            if (total <= 0) return 0.0;
            long used = total - store.getUsableSpace();
            return Math.min(100.0, used * 100.0 / total);
        } catch (Exception e) {
            return null;
        }
    }

    private long dirSize(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return 0L;
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (Exception e) {
                    return 0L;
                }
            }).sum();
        }
    }

    private Map<String, Object> deviceStats() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("online", deviceRepository.countByStatus(Device.DeviceStatus.online));
        row.put("offline", deviceRepository.countByStatus(Device.DeviceStatus.offline));
        row.put("busy", deviceRepository.countByStatus(Device.DeviceStatus.busy));
        row.put("error", deviceRepository.countByStatus(Device.DeviceStatus.error));
        row.put("total", deviceRepository.count());
        return row;
    }

    private Map<String, Object> schedulerStats() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("queue_size", taskQueue.size());
        row.put("queue_max", properties.getScheduler().getQueueMaxSize());
        row.put("running_tasks", taskRepository.countByStatus(TestTask.TaskStatus.running));
        row.put("queued_tasks", taskRepository.countByStatus(TestTask.TaskStatus.queued));
        row.put("pending_tasks", taskRepository.countByStatus(TestTask.TaskStatus.pending));
        return row;
    }
}
