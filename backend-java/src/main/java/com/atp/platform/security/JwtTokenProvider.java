package com.atp.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final int expireHours;

    public JwtTokenProvider(@Value("${atp.jwt.secret}") String secret,
                            @Value("${atp.jwt.expire-hours}") int expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String generateToken(Long userId, String username, String role, Long teamId) {
        Instant now = Instant.now();
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("user_id", userId);
        claims.put("role", role);
        if (teamId != null) {
            claims.put("team_id", teamId);
        }
        return Jwts.builder()
                .subject(username)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Instant getExpirationTime() {
        return Instant.now().plus(expireHours, ChronoUnit.HOURS);
    }
}
