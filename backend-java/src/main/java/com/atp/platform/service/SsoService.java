package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.User;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.UserRepository;
import com.atp.platform.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SsoService {

    private final AtpProperties properties;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;

    public Map<String, Object> config() {
        AtpProperties.Sso sso = properties.getSso();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("enabled", sso.isEnabled());
        row.put("provider_name", sso.getProviderName());
        row.put("mock_mode", sso.isMockMode());
        row.put("authorize_url", sso.getAuthorizeUrl());
        return row;
    }

    public Map<String, Object> exchange(String ssoToken, String ip, String userAgent) {
        AtpProperties.Sso sso = properties.getSso();
        if (!sso.isEnabled()) {
            throw new AppException("SSO_DISABLED", "SSO 未启用", HttpStatus.BAD_REQUEST);
        }
        String username = resolveUsername(sso, ssoToken);
        User user = userRepository.findByUsernameAndStatus(username, (byte) 1)
                .orElseThrow(() -> new AppException("SSO", "SSO 用户未同步至平台: " + username, HttpStatus.UNAUTHORIZED));
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        user = userRepository.save(user);
        int tv = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        String jti = jwtTokenProvider.newJti();
        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getUsername(), user.getRole().name(), user.getTeamId(), tv, jti);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(jwtTokenProvider.getExpirationTime(), ZoneId.systemDefault());
        authService.createSession(user.getId(), jti, ip, userAgent, expiresAt);
        user.setPasswordHash(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("expires_at", jwtTokenProvider.getExpirationTime());
        data.put("user", user);
        data.put("preferences", authService.parsePreferences(user));
        return data;
    }

    private static String resolveUsername(AtpProperties.Sso sso, String ssoToken) {
        if (ssoToken == null || ssoToken.isBlank()) {
            throw new AppException("SSO", "sso_token 必填", HttpStatus.BAD_REQUEST);
        }
        if (sso.isMockMode()) {
            String prefix = sso.getMockSecret() + ":";
            if (!ssoToken.startsWith(prefix)) {
                throw new AppException("SSO", "无效的 SSO 令牌", HttpStatus.UNAUTHORIZED);
            }
            String username = ssoToken.substring(prefix.length()).trim();
            if (username.isBlank()) {
                throw new AppException("SSO", "SSO 令牌缺少用户名", HttpStatus.BAD_REQUEST);
            }
            return username;
        }
        throw new AppException("SSO", "生产 SSO 回调尚未配置", HttpStatus.NOT_IMPLEMENTED);
    }
}
