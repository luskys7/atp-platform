package com.atp.platform.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("local")
public class InMemoryDeviceLockService implements DeviceLockService {

    private final Map<Long, LockEntry> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(Long deviceId, Long taskId, int ttlSeconds) {
        cleanExpired();
        LockEntry existing = locks.get(deviceId);
        if (existing != null && existing.expiresAt.isAfter(Instant.now()) && !existing.taskId.equals(taskId)) {
            return false;
        }
        locks.put(deviceId, new LockEntry(taskId, Instant.now().plusSeconds(ttlSeconds)));
        return true;
    }

    @Override
    public void releaseLock(Long deviceId) {
        locks.remove(deviceId);
    }

    @Override
    public boolean renewLock(Long deviceId, Long taskId, int ttlSeconds) {
        LockEntry entry = locks.get(deviceId);
        if (entry == null || !entry.taskId.equals(taskId)) {
            return false;
        }
        locks.put(deviceId, new LockEntry(taskId, Instant.now().plusSeconds(ttlSeconds)));
        return true;
    }

    @Override
    public void releaseLocksForTask(Long taskId) {
        cleanExpired();
        locks.entrySet().removeIf(e -> e.getValue().taskId.equals(taskId));
    }

    private void cleanExpired() {
        Instant now = Instant.now();
        locks.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    private record LockEntry(Long taskId, Instant expiresAt) {}
}
