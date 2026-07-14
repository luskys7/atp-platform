package com.atp.platform.service;

import com.atp.platform.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Profile("!local")
@RequiredArgsConstructor
public class RedisDeviceLockService implements DeviceLockService {

    private final StringRedisTemplate redis;

    @Override
    public boolean tryLock(Long deviceId, Long taskId, int ttlSeconds) {
        String key = AppConfig.DEVICE_LOCK_PREFIX + deviceId;
        Boolean ok = redis.opsForValue().setIfAbsent(key, taskId.toString(), Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void releaseLocksForTask(Long taskId) {
        // Redis 锁按 deviceId 存储，由 DeviceService.releaseLocksForTask 逐设备释放
    }

    @Override
    public void releaseLock(Long deviceId) {
        redis.delete(AppConfig.DEVICE_LOCK_PREFIX + deviceId);
    }

    @Override
    public boolean renewLock(Long deviceId, Long taskId, int ttlSeconds) {
        String key = AppConfig.DEVICE_LOCK_PREFIX + deviceId;
        String val = redis.opsForValue().get(key);
        if (val == null || !val.equals(taskId.toString())) {
            return false;
        }
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
        return true;
    }
}
