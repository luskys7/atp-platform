package com.atp.platform.security;

public record AuthUser(Long userId, String role, Long teamId, String jti) {
    public AuthUser(Long userId, String role, Long teamId) {
        this(userId, role, teamId, null);
    }
}
