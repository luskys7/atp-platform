package com.atp.platform.security;

public record AuthUser(Long userId, String role, Long teamId) {
}
