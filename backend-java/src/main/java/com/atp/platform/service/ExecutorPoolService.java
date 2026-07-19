package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorPoolService {

    /** 连续心跳失败达到该次数后判定离线并写入故障记录（与监控说明一致） */
    private static final int OFFLINE_FAIL_THRESHOLD = 3;
    private static final int MAX_EVENTS = 50;

    private final AtpProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ConcurrentHashMap<String, Boolean> healthCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> failStreak = new ConcurrentHashMap<>();
    /** 当前离线周期内是否已写过故障记录，避免重复刷屏 */
    private final ConcurrentHashMap<String, Boolean> offlineEventRecorded = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Map<String, Object>> failoverEvents = new ConcurrentLinkedDeque<>();

    public List<String> allUrls() {
        List<String> urls = new ArrayList<>();
        String primary = properties.getExecutor().getUrl();
        if (primary != null && !primary.isBlank()) {
            urls.add(primary.trim());
        }
        if (properties.getExecutor().getFallbackUrls() != null) {
            for (String u : properties.getExecutor().getFallbackUrls()) {
                if (u != null && !u.isBlank() && !urls.contains(u.trim())) {
                    urls.add(u.trim());
                }
            }
        }
        return urls;
    }

    public List<String> healthyUrls() {
        List<String> healthy = new ArrayList<>();
        for (String url : allUrls()) {
            if (healthCache.getOrDefault(url, true)) {
                healthy.add(url);
            }
        }
        return healthy.isEmpty() ? allUrls() : healthy;
    }

    public Map<String, Object> snapshot() {
        // 缓存为空时做一次探测；平时由定时任务与监控实时探测更新
        if (healthCache.isEmpty() && !allUrls().isEmpty()) {
            refreshHealth();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String url : allUrls()) {
            boolean up = Boolean.TRUE.equals(healthCache.get(url));
            nodes.add(Map.of("url", url, "status", up ? "up" : "down"));
        }
        row.put("nodes", nodes);
        row.put("primary", properties.getExecutor().getUrl());
        long upCount = nodes.stream().filter(n -> "up".equals(n.get("status"))).count();
        row.put("status", upCount == 0 ? "down" : (upCount < nodes.size() ? "degraded" : "up"));
        row.put("healthy_count", upCount);
        row.put("total_count", nodes.size());
        return row;
    }

    public List<Map<String, Object>> recentFailoverEvents() {
        return new ArrayList<>(failoverEvents);
    }

    public void clearFailoverEvents() {
        failoverEvents.clear();
    }

    public void recordFailover(String fromUrl, String toUrl, Long taskId, Long executionId, String reason) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("at", java.time.LocalDateTime.now().toString());
        ev.put("from_url", fromUrl != null ? fromUrl : "");
        ev.put("to_url", toUrl != null && !toUrl.isBlank() ? toUrl : "无可用备用节点");
        ev.put("task_id", taskId);
        ev.put("execution_id", executionId);
        ev.put("reason", reason != null ? reason : "failover");
        failoverEvents.addFirst(ev);
        while (failoverEvents.size() > MAX_EVENTS) {
            failoverEvents.removeLast();
        }
        log.info("Executor failover: {} -> {} task={} ({})", fromUrl, toUrl, taskId, reason);
    }

    @Scheduled(fixedDelayString = "${atp.executor.health-check-interval-ms:60000}")
    public void refreshHealth() {
        for (String url : allUrls()) {
            boolean up;
            String err = null;
            try {
                restClient.get().uri(url + "/health").retrieve().toBodilessEntity();
                up = true;
            } catch (Exception e) {
                up = false;
                err = e.getMessage();
                log.warn("Executor unhealthy: {} ({})", url, err);
            }
            applyHealthResult(url, up, err);
        }
    }

    /** 监控实时探测回调：节点恢复 */
    public void onNodeHealthy(String url) {
        if (url == null || url.isBlank()) return;
        applyHealthResult(url.trim(), true, null);
    }

    /** 监控实时探测回调：节点失败（计入连续失败，达阈值后写故障记录） */
    public void onNodeUnhealthy(String url, String errorMessage) {
        if (url == null || url.isBlank()) return;
        applyHealthResult(url.trim(), false, errorMessage);
    }

    void markHealthy(String url) {
        onNodeHealthy(url);
    }

    void markUnhealthy(String url) {
        onNodeUnhealthy(url, "marked_unhealthy");
    }

    private void applyHealthResult(String url, boolean up, String errorMessage) {
        Boolean previous = healthCache.put(url, up);
        if (up) {
            failStreak.put(url, 0);
            offlineEventRecorded.put(url, false);
            return;
        }

        int streak = failStreak.merge(url, 1, Integer::sum);
        // 首次从健康→失败立刻记一次，便于监控页即时可见；达到 3 次再补一条「连续失联」说明
        boolean firstFail = previous == null || previous;
        boolean hitThreshold = streak == OFFLINE_FAIL_THRESHOLD;
        if (firstFail && !Boolean.TRUE.equals(offlineEventRecorded.get(url))) {
            recordOfflineEvent(url, "unhealthy", errorMessage);
            offlineEventRecorded.put(url, true);
        } else if (hitThreshold) {
            // 连续 3 次心跳失联，追加一条说明记录
            recordOfflineEvent(url, "heartbeat_lost_3", errorMessage);
        }
    }

    private void recordOfflineEvent(String fromUrl, String reason, String errorMessage) {
        String fallback = null;
        for (String u : allUrls()) {
            if (!u.equalsIgnoreCase(fromUrl) && Boolean.TRUE.equals(healthCache.get(u))) {
                fallback = u;
                break;
            }
        }
        String toUrl = fallback != null ? fallback : "无可用备用节点";
        String detailReason = reason;
        if (fallback == null) {
            detailReason = "node_offline_no_backup";
        } else if ("heartbeat_lost_3".equals(reason)) {
            detailReason = "heartbeat_lost_3";
        } else {
            detailReason = "node_offline_failover";
        }
        if (errorMessage != null && !errorMessage.isBlank() && errorMessage.length() < 120) {
            // 保留简短错误信息到 reason 旁，前端会做中文映射；长错误不塞进 reason
        }
        recordFailover(fromUrl, toUrl, null, null, detailReason);
    }
}
