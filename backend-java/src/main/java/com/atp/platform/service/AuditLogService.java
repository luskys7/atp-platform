package com.atp.platform.service;

import com.atp.platform.entity.AuditLog;
import com.atp.platform.entity.User;
import com.atp.platform.repository.AuditLogRepository;
import com.atp.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public Map<String, Object> list(int page, int pageSize) {
        Page<AuditLog> result = auditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, page - 1), Math.min(pageSize, 100)));
        List<Long> userIds = result.getContent().stream().map(AuditLog::getUserId).distinct().toList();
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> list = result.getContent().stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("user_id", log.getUserId());
            User u = users.get(log.getUserId());
            row.put("username", u != null ? u.getUsername() : null);
            row.put("display_name", u != null ? u.getDisplayName() : null);
            row.put("action", log.getAction());
            row.put("resource_type", log.getResourceType());
            row.put("resource_id", log.getResourceId());
            row.put("detail", log.getDetail());
            row.put("ip", log.getIp());
            row.put("created_at", log.getCreatedAt());
            return row;
        }).toList();
        return Map.of("list", list, "total", result.getTotalElements(), "page", page, "page_size", pageSize);
    }
}
