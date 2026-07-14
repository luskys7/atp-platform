package com.atp.platform.service;

public interface DeviceLockService {
    boolean tryLock(Long deviceId, Long taskId, int ttlSeconds);
    void releaseLock(Long deviceId);
    boolean renewLock(Long deviceId, Long taskId, int ttlSeconds);
    void releaseLocksForTask(Long taskId);
}
