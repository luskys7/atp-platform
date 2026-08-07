package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.dto.LoginRequest;
import com.atp.platform.entity.LoginLog;
import com.atp.platform.entity.User;
import com.atp.platform.entity.UserSession;
import com.atp.platform.security.AuthUser;
import com.atp.platform.security.JwtTokenProvider;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.AuthService;
import com.atp.platform.service.SsoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SsoService ssoService;
    private final ObjectMapper objectMapper;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String ip = clientIp(request);
        String ua = request.getHeader("User-Agent");
        User user = authService.login(req.getUsername(), req.getPassword(), ip, ua);
        int tv = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        String jti = jwtTokenProvider.newJti();
        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getUsername(), user.getRole().name(), user.getTeamId(), tv, jti);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(jwtTokenProvider.getExpirationTime(), ZoneId.systemDefault());
        authService.createSession(user.getId(), jti, ip, ua, expiresAt);
        user.setPasswordHash(null);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("expires_at", jwtTokenProvider.getExpirationTime());
        data.put("user", user);
        data.put("preferences", authService.parsePreferences(user));
        return ApiResponse.ok(data);
    }

    @GetMapping("/sso/config")
    public ApiResponse<Map<String, Object>> ssoConfig() {
        return ApiResponse.ok(ssoService.config());
    }

    @PostMapping("/sso/login")
    public ApiResponse<Map<String, Object>> ssoLogin(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return ApiResponse.ok(ssoService.exchange(body.get("sso_token"), clientIp(request), request.getHeader("User-Agent")));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        User user = authService.getUserById(SecurityUtils.currentUserId());
        user.setPasswordHash(null);
        Map<String, Object> data = new HashMap<>();
        data.put("user", user);
        data.put("preferences", authService.parsePreferences(user));
        return ApiResponse.ok(data);
    }

    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(@RequestBody Map<String, Object> body) {
        String prefsJson = null;
        if (body.get("preferences") != null) {
            try {
                prefsJson = objectMapper.writeValueAsString(body.get("preferences"));
            } catch (Exception ignored) {
            }
        }
        authService.updateProfile(
                SecurityUtils.currentUserId(),
                str(body.get("display_name")),
                str(body.get("phone")),
                str(body.get("email")),
                prefsJson
        );
        return ApiResponse.ok();
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = authService.uploadAvatar(SecurityUtils.currentUserId(), file);
        return ApiResponse.ok(Map.of("avatar_url", url));
    }

    @GetMapping("/avatar/{filename}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        Path path = authService.resolveAvatarFile(filename);
        String name = filename.toLowerCase();
        MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG
                : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                : name.endsWith(".gif") ? MediaType.IMAGE_GIF
                : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).body(new FileSystemResource(path));
    }

    @PutMapping("/password")
    public ApiResponse<Map<String, Object>> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(
                SecurityUtils.currentUserId(),
                body.get("old_password"),
                body.get("new_password"),
                body.get("confirm_password")
        );
        return ApiResponse.ok(Map.of(
                "message", "密码修改成功，所有终端会话将被强制下线，请重新登录",
                "force_logout", true
        ));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<UserSession>> sessions() {
        return ApiResponse.ok(authService.listActiveSessions(SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> revokeSession(@PathVariable Long id) {
        authService.revokeSession(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/sessions/revoke-others")
    public ApiResponse<Map<String, Object>> revokeOthers() {
        String jti = currentJti();
        int n = authService.revokeOtherSessions(SecurityUtils.currentUserId(), jti);
        return ApiResponse.ok(Map.of("revoked", n));
    }

    @GetMapping("/login-logs")
    public ApiResponse<List<LoginLog>> loginLogs() {
        return ApiResponse.ok(authService.loginLogs(SecurityUtils.currentUserId()));
    }

    @GetMapping("/api-keys")
    public ApiResponse<List<Map<String, Object>>> apiKeys() {
        return ApiResponse.ok(authService.listApiKeys(SecurityUtils.currentUserId()));
    }

    @PostMapping("/api-keys")
    public ApiResponse<Map<String, Object>> createApiKey(@RequestBody(required = false) Map<String, String> body) {
        String name = body != null ? body.get("name") : null;
        return ApiResponse.ok(authService.createApiKey(SecurityUtils.currentUserId(), name));
    }

    @PostMapping("/api-keys/{id}/revoke")
    public ApiResponse<Void> revokeApiKey(@PathVariable Long id) {
        authService.revokeApiKey(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<List<Map<String, Object>>> listUsers() {
        return ApiResponse.ok(authService.listUsersBrief());
    }

    private String currentJti() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user.jti();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    private static String str(Object v) {
        return v != null ? v.toString() : null;
    }
}
