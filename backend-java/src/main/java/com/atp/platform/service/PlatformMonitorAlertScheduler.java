package com.atp.platform.service;

import com.atp.platform.entity.User;
import com.atp.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformMonitorAlertScheduler {

    private final PlatformMonitorService monitorService;
    private final PlatformMessageService messageService;
    private final UserRepository userRepository;

    private volatile String lastOverall = "healthy";

    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    public void checkAndAlert() {
        Map<String, Object> snap = monitorService.snapshot();
        String overall = String.valueOf(snap.getOrDefault("overall", "healthy"));
        if ("healthy".equals(overall)) {
            lastOverall = overall;
            return;
        }
        if (overall.equals(lastOverall)) {
            return;
        }
        lastOverall = overall;
        String title = "degraded".equals(overall) ? "平台健康：部分组件异常" : "平台健康：严重异常";
        StringBuilder content = new StringBuilder();
        content.append("检测时间：").append(snap.get("checked_at")).append("\n");
        appendIfDown(content, "数据库", snap.get("database"));
        appendIfDown(content, "执行器", snap.get("executor"));
        appendIfDown(content, "存储", snap.get("storage"));
        @SuppressWarnings("unchecked")
        Map<String, Object> pool = (Map<String, Object>) snap.get("executor_pool");
        if (pool != null && !"up".equals(String.valueOf(pool.get("status")))) {
            content.append("执行器池：").append(pool.get("status")).append("\n");
        }
        for (User u : adminUsers()) {
            messageService.sendPlatformAlert(u.getId(), title, content.toString().trim());
        }
        log.warn("Platform health alert sent: {}", overall);
    }

    @SuppressWarnings("unchecked")
    private static void appendIfDown(StringBuilder sb, String label, Object node) {
        if (!(node instanceof Map<?, ?> m)) return;
        String status = String.valueOf(m.get("status"));
        if (!"up".equals(status)) {
            sb.append(label).append("：").append(status);
            if (m.get("alert") != null) sb.append(" (").append(m.get("alert")).append(")");
            else if (m.get("error") != null) sb.append(" (").append(m.get("error")).append(")");
            sb.append("\n");
        }
    }

    private List<User> adminUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .filter(u -> u.getRole() == User.UserRole.super_admin || u.getRole() == User.UserRole.test_admin)
                .toList();
    }
}
