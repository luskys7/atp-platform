package com.atp.platform.service;

import com.atp.platform.entity.AuditLog;
import com.atp.platform.entity.LoginLog;
import com.atp.platform.entity.User;
import com.atp.platform.entity.UserApiKey;
import com.atp.platform.entity.UserSession;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.AuditLogRepository;
import com.atp.platform.repository.LoginLogRepository;
import com.atp.platform.repository.UserApiKeyRepository;
import com.atp.platform.repository.UserRepository;
import com.atp.platform.repository.UserSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserSessionRepository sessionRepository;
    private final LoginLogRepository loginLogRepository;
    private final UserApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public User login(String username, String password, String ip, String userAgent) {
        UserAgentInfo ua = parseUserAgent(userAgent);
        try {
            User user = userRepository.findByUsernameAndStatus(username, (byte) 1)
                    .orElseThrow(() -> new AppException("AUTH", "用户名或密码错误", HttpStatus.UNAUTHORIZED));
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                saveLoginLog(user.getId(), ip, ua, "failed", false, "密码错误");
                throw new AppException("AUTH", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
            }
            boolean remote = user.getLastLoginIp() != null
                    && !user.getLastLoginIp().isBlank()
                    && ip != null
                    && !ip.equals(user.getLastLoginIp());
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(ip);
            user = userRepository.save(user);
            saveLoginLog(user.getId(), ip, ua, "success", remote, remote ? "异地登录" : "登录成功");
            return user;
        } catch (AppException e) {
            if ("AUTH".equals(e.getErrorCode()) && e.getMessage() != null && e.getMessage().contains("用户名或密码")) {
                // username not found — no user id to log
            }
            throw e;
        }
    }

    public UserSession createSession(Long userId, String jti, String ip, String userAgent, LocalDateTime expiresAt) {
        UserAgentInfo ua = parseUserAgent(userAgent);
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setJti(jti);
        session.setIp(ip);
        session.setUserAgent(userAgent);
        session.setDeviceLabel(ua.device());
        session.setBrowser(ua.browser());
        session.setActive(true);
        session.setExpiresAt(expiresAt);
        return sessionRepository.save(session);
    }

    public boolean isSessionActive(String jti) {
        if (jti == null || jti.isBlank()) return true; // 兼容旧 token
        return sessionRepository.findByJtiAndActiveTrue(jti).isPresent();
    }

    public boolean isTokenVersionValid(Long userId, Integer tokenVersion) {
        User user = getUserById(userId);
        int current = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        if (tokenVersion == null) {
            return current == 0;
        }
        return tokenVersion == current;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND));
    }

    public void updateProfile(Long userId, String displayName, String phone, String email, String preferencesJson) {
        User user = getUserById(userId);
        if (displayName != null) user.setDisplayName(displayName);
        if (phone != null) user.setPhone(phone);
        if (email != null) user.setEmail(email);
        if (preferencesJson != null) user.setPreferencesJson(preferencesJson);
        userRepository.save(user);
    }

    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("BAD_REQUEST", "请选择头像文件", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (!contentType.startsWith("image/")) {
            throw new AppException("BAD_REQUEST", "仅支持图片文件", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new AppException("BAD_REQUEST", "头像大小不能超过 2MB", HttpStatus.BAD_REQUEST);
        }
        try {
            Path dir = Path.of("data", "avatars");
            Files.createDirectories(dir);
            String ext = contentType.contains("png") ? ".png"
                    : contentType.contains("webp") ? ".webp"
                    : contentType.contains("gif") ? ".gif" : ".jpg";
            String filename = "u" + userId + "_" + System.currentTimeMillis() + ext;
            Path target = dir.resolve(filename);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            String url = "/api/v1/auth/avatar/" + filename;
            User user = getUserById(userId);
            user.setAvatarUrl(url);
            userRepository.save(user);
            return url;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("UPLOAD_FAILED", "头像上传失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public Path resolveAvatarFile(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new AppException("NOT_FOUND", "头像不存在", HttpStatus.NOT_FOUND);
        }
        Path path = Path.of("data", "avatars", filename);
        if (!Files.isRegularFile(path)) {
            throw new AppException("NOT_FOUND", "头像不存在", HttpStatus.NOT_FOUND);
        }
        return path;
    }

    @Transactional
    public void changePassword(Long userId, String oldPass, String newPass, String confirmPass) {
        if (oldPass == null || oldPass.isBlank()) {
            throw new AppException("BAD_REQUEST", "原密码不能为空", HttpStatus.BAD_REQUEST);
        }
        if (newPass == null || newPass.isBlank()) {
            throw new AppException("BAD_REQUEST", "新密码不能为空", HttpStatus.BAD_REQUEST);
        }
        if (confirmPass != null && !newPass.equals(confirmPass)) {
            throw new AppException("BAD_REQUEST", "两次输入的新密码不一致", HttpStatus.BAD_REQUEST);
        }
        validatePasswordComplexity(newPass);
        User user = getUserById(userId);
        if (!passwordEncoder.matches(oldPass, user.getPasswordHash())) {
            throw new AppException("AUTH", "原密码错误", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(newPass));
        int tv = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        user.setTokenVersion(tv + 1);
        userRepository.save(user);
        sessionRepository.revokeAllByUserId(userId);
    }

    public void validatePasswordComplexity(String password) {
        if (password.length() < 8 || password.length() > 32) {
            throw new AppException("BAD_REQUEST", "密码长度需为 8~32 位", HttpStatus.BAD_REQUEST);
        }
        int score = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) score++;
        if (password.chars().anyMatch(Character::isUpperCase)) score++;
        if (password.chars().anyMatch(Character::isDigit)) score++;
        if (password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) score++;
        if (score < 3) {
            throw new AppException("BAD_REQUEST", "密码需至少包含大小写字母、数字、符号中的三类", HttpStatus.BAD_REQUEST);
        }
    }

    public List<UserSession> listActiveSessions(Long userId) {
        return sessionRepository.findByUserIdAndActiveTrueOrderByLoginAtDesc(userId);
    }

    @Transactional
    public void revokeSession(Long userId, Long sessionId) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "会话不存在", HttpStatus.NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new AppException("FORBIDDEN", "无权操作该会话", HttpStatus.FORBIDDEN);
        }
        session.setActive(false);
        session.setRevokedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public int revokeOtherSessions(Long userId, String keepJti) {
        if (keepJti == null || keepJti.isBlank()) {
            return sessionRepository.revokeAllByUserId(userId);
        }
        return sessionRepository.revokeOthers(userId, keepJti);
    }

    public List<LoginLog> loginLogs(Long userId) {
        return loginLogRepository.findByUserIdAndLoginAtAfterOrderByLoginAtDesc(
                userId, LocalDateTime.now().minusDays(90));
    }

    public List<Map<String, Object>> listApiKeys(Long userId) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(k -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", k.getId());
                    row.put("name", k.getName());
                    row.put("key_prefix", k.getKeyPrefix());
                    row.put("active", k.getActive());
                    row.put("created_at", k.getCreatedAt());
                    row.put("last_used_at", k.getLastUsedAt());
                    row.put("revoked_at", k.getRevokedAt());
                    return row;
                })
                .toList();
    }

    public Map<String, Object> createApiKey(Long userId, String name) {
        String raw = "atp_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UserApiKey key = new UserApiKey();
        key.setUserId(userId);
        key.setName(name != null && !name.isBlank() ? name : "默认密钥");
        key.setKeyPrefix(raw.substring(0, 12));
        key.setKeyHash(sha256(raw));
        key.setActive(true);
        apiKeyRepository.save(key);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", key.getId());
        resp.put("name", key.getName());
        resp.put("key_prefix", key.getKeyPrefix());
        resp.put("api_key", raw);
        resp.put("created_at", key.getCreatedAt());
        return resp;
    }

    @Transactional
    public void revokeApiKey(Long userId, Long keyId) {
        UserApiKey key = apiKeyRepository.findByIdAndUserId(keyId, userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "密钥不存在", HttpStatus.NOT_FOUND));
        key.setActive(false);
        key.setRevokedAt(LocalDateTime.now());
        apiKeyRepository.save(key);
    }

    public Map<String, Object> defaultPreferences() {
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("report_export_format", "pdf");
        prefs.put("dashboard_refresh_seconds", 30);
        prefs.put("notify_task_done", true);
        prefs.put("notify_task_failed", true);
        prefs.put("notify_audit_change", false);
        return prefs;
    }

    public Map<String, Object> parsePreferences(User user) {
        Map<String, Object> defaults = defaultPreferences();
        if (user.getPreferencesJson() == null || user.getPreferencesJson().isBlank()) {
            return defaults;
        }
        try {
            Map<String, Object> stored = objectMapper.readValue(user.getPreferencesJson(), new TypeReference<>() {});
            defaults.putAll(stored);
            return defaults;
        } catch (Exception e) {
            return defaults;
        }
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

    private void saveLoginLog(Long userId, String ip, UserAgentInfo ua, String status, boolean remote, String message) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setIp(ip);
        log.setDeviceLabel(ua.device());
        log.setBrowser(ua.browser());
        log.setUserAgent(ua.raw());
        log.setStatus(status);
        log.setRemoteLogin(remote);
        log.setMessage(message);
        loginLogRepository.save(log);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserAgentInfo parseUserAgent(String ua) {
        String raw = ua != null ? ua : "";
        String lower = raw.toLowerCase();
        String browser = "未知浏览器";
        if (lower.contains("edg/")) browser = "Edge";
        else if (lower.contains("chrome/")) browser = "Chrome";
        else if (lower.contains("firefox/")) browser = "Firefox";
        else if (lower.contains("safari/") && !lower.contains("chrome")) browser = "Safari";

        String device = "桌面端";
        if (lower.contains("iphone") || lower.contains("ipad")) device = "iOS 设备";
        else if (lower.contains("android")) device = "Android 设备";
        else if (lower.contains("windows")) device = "Windows";
        else if (lower.contains("mac os") || lower.contains("macintosh")) device = "macOS";
        else if (lower.contains("linux")) device = "Linux";

        return new UserAgentInfo(device, browser, raw);
    }

    public record UserAgentInfo(String device, String browser, String raw) {}
}
