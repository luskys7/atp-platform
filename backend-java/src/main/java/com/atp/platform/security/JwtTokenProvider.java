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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final int expireHours;

    public JwtTokenProvider(@Value("${atp.jwt.secret}") String secret,
                            @Value("${atp.jwt.expire-hours}") int expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String generateToken(Long userId, String username, String role, Long teamId, int tokenVersion, String jti) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", userId);
        claims.put("role", role);
        claims.put("tv", tokenVersion);
        claims.put("jti", jti);
        if (teamId != null) {
            claims.put("team_id", teamId);
        }
        return Jwts.builder()
                .id(jti)
                .subject(username)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
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

    public int getExpireHours() {
        return expireHours;
    }
}
