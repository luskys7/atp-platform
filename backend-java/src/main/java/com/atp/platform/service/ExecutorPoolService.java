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

    private final AtpProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ConcurrentHashMap<String, Boolean> healthCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Map<String, Object>> failoverEvents = new ConcurrentLinkedDeque<>();
    private static final int MAX_EVENTS = 50;

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

    public void recordFailover(String fromUrl, String toUrl, Long taskId, Long executionId, String reason) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("at", java.time.LocalDateTime.now().toString());
        ev.put("from_url", fromUrl);
        ev.put("to_url", toUrl);
        ev.put("task_id", taskId);
        ev.put("execution_id", executionId);
        ev.put("reason", reason);
        failoverEvents.addFirst(ev);
        while (failoverEvents.size() > MAX_EVENTS) {
            failoverEvents.removeLast();
        }
        log.info("Executor failover: {} -> {} task={} ({})", fromUrl, toUrl, taskId, reason);
    }

    @Scheduled(fixedDelayString = "${atp.executor.health-check-interval-ms:60000}")
    public void refreshHealth() {
        for (String url : allUrls()) {
            try {
                restClient.get().uri(url + "/health").retrieve().toBodilessEntity();
                healthCache.put(url, true);
            } catch (Exception e) {
                healthCache.put(url, false);
                log.warn("Executor unhealthy: {} ({})", url, e.getMessage());
            }
        }
    }

    void markHealthy(String url) {
        healthCache.put(url, true);
    }

    void markUnhealthy(String url) {
        healthCache.put(url, false);
    }
}
