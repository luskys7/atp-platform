package com.atp.platform.service;

import com.atp.platform.entity.AuditLog;
import com.atp.platform.entity.User;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.AuditLogRepository;
import com.atp.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public User login(String username, String password) {
        User user = userRepository.findByUsernameAndStatus(username, (byte) 1)
                .orElseThrow(() -> new AppException("AUTH", "用户名或密码错误", HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AppException("AUTH", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
    }

    public void updateProfile(Long userId, String displayName) {
        User user = getUserById(userId);
        user.setDisplayName(displayName);
        userRepository.save(user);
    }

    public void changePassword(Long userId, String oldPass, String newPass) {
        User user = getUserById(userId);
        if (!passwordEncoder.matches(oldPass, user.getPasswordHash())) {
            throw new AppException("AUTH", "原密码错误", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(newPass));
        userRepository.save(user);
    }

    public List<Map<String, Object>> listUsersBrief() {
        return userRepository.findAll().stream()
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .map(u -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", u.getId());
                    row.put("username", u.getUsername());
                    row.put("display_name", u.getDisplayName());
                    row.put("role", u.getRole().name());
                    row.put("team_id", u.getTeamId());
                    return row;
                })
                .toList();
    }

    public void createAuditLog(Long userId, String action, String resourceType, String resourceId, String detail, String ip) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetail(detail);
        log.setIp(ip);
        auditLogRepository.save(log);
    }
}
