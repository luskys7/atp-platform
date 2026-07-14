package com.atp.platform.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScreenStreamTokenService {

    private final Map<String, StreamSession> sessions = new ConcurrentHashMap<>();

    public record StreamSession(String token, Long deviceId, String serialNumber,
                                Long userId, Instant expiresAt) {}

    public StreamSession createSession(Long deviceId, String serialNumber, Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        StreamSession session = new StreamSession(
                token, deviceId, serialNumber, userId, Instant.now().plusSeconds(1800));
        sessions.put(token, session);
        return session;
    }

    public boolean verify(String token, String serialNumber) {
        StreamSession session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return false;
        }
        return session.serialNumber().equals(serialNumber);
    }

    public void revoke(String token) {
        sessions.remove(token);
    }

    public StreamSession get(String token) {
        return sessions.get(token);
    }
}
