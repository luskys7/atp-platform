package com.atp.platform.service;

import com.atp.platform.dto.DeviceRegisterRequest;
import com.atp.platform.entity.Device;
import com.atp.platform.entity.DeviceWhitelist;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.exception.ErrorCodes;
import com.atp.platform.repository.DeviceRepository;
import com.atp.platform.repository.DeviceWhitelistRepository;
import com.atp.platform.repository.TaskExecutionRepository;
import com.atp.platform.repository.TestTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceWhitelistRepository whitelistRepository;
    private final DeviceLockService lockService;
    private final TestTaskRepository taskRepository;
    private final TaskExecutionRepository executionRepository;
    private final PythonExecutorClient executorClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TeamScopeService teamScope;
    private final IosWdaService iosWdaService;

    public Device register(DeviceRegisterRequest req) {
        whitelistRepository.findBySerialNumber(req.getSerialNumber())
                .orElseThrow(() -> new AppException(ErrorCodes.E1001, HttpStatus.FORBIDDEN));

        Device device = deviceRepository.findBySerialNumber(req.getSerialNumber()).orElse(new Device());
        device.setSerialNumber(req.getSerialNumber());
        device.setName(req.getName());
        device.setPlatform(Device.Platform.valueOf(req.getPlatform()));
        device.setOsVersion(req.getOsVersion());
        device.setModel(req.getModel());
        device.setStatus(Device.DeviceStatus.online);
        device.setAgentHost(req.getAgentHost());
        device.setAgentPort(req.getAgentPort());
        device.setScreenWidth(req.getScreenWidth());
        device.setScreenHeight(req.getScreenHeight());
        device.setWdaPort(req.getWdaPort());
        device.setAdbPort(req.getAdbPort() != null ? req.getAdbPort() : 5037);
        device.setBatteryLevel(req.getBatteryLevel() != null ? req.getBatteryLevel() : 0);
        device.setLastHeartbeatAt(LocalDateTime.now());
        device.setIsWhitelisted(true);
        if (device.getTeamId() == null) {
            device.setTeamId(1L);
        }
        Device saved = deviceRepository.save(device);
        if (saved.getPlatform() == Device.Platform.ios) {
            iosWdaService.tryAutoDeploy(saved);
        }
        return saved;
    }

    public void heartbeat(String serialNumber, Integer batteryLevel) {
        Device device = deviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new AppException(ErrorCodes.E1001, HttpStatus.FORBIDDEN));
        device.setLastHeartbeatAt(LocalDateTime.now());
        device.setBatteryLevel(batteryLevel != null ? batteryLevel : 0);
        device.setStatus(Device.DeviceStatus.online);
        deviceRepository.save(device);
    }

    public Page<Device> list(int page, int pageSize, String platform, String status) {
        Device.Platform p = platform != null && !platform.isBlank() ? Device.Platform.valueOf(platform) : null;
        Device.DeviceStatus s = status != null && !status.isBlank() ? Device.DeviceStatus.valueOf(status) : null;
        return deviceRepository.findByFilters(p, s, teamScope.scopeTeamId(), PageRequest.of(page - 1, pageSize));
    }

    public Device getById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "设备不存在", HttpStatus.NOT_FOUND));
        teamScope.assertTeamAccess(device.getTeamId());
        return device;
    }

    public void updateStatus(Long id, Device.DeviceStatus status) {
        Device device = getById(id);
        device.setStatus(status);
        deviceRepository.save(device);
    }

    public Device updateCalibration(Long id, String calibrationJson) {
        Device device = getById(id);
        device.setCalibrationJson(calibrationJson);
        return deviceRepository.save(device);
    }

    public void delete(Long id) {
        Device device = getById(id);
        if (device.getLockedByTaskId() != null) {
            throw new AppException(ErrorCodes.E1002, HttpStatus.CONFLICT);
        }
        deviceRepository.delete(device);
    }

    public DeviceWhitelist addWhitelist(String serialNumber, String platform, String remark, Long createdBy) {
        DeviceWhitelist entry = new DeviceWhitelist();
        entry.setSerialNumber(serialNumber);
        entry.setPlatform(Device.Platform.valueOf(platform));
        entry.setRemark(remark);
        entry.setCreatedBy(createdBy);
        return whitelistRepository.save(entry);
    }

    public Page<DeviceWhitelist> listWhitelist(int page, int pageSize) {
        return whitelistRepository.findAll(PageRequest.of(page - 1, pageSize));
    }

    public void removeWhitelist(Long id) {
        whitelistRepository.deleteById(id);
    }

    public List<Device> getAvailableDevices(Device.Platform platform, int count) {
        return deviceRepository.findAvailable(platform, teamScope.scopeTeamId(), PageRequest.of(0, count));
    }

    /** 按任务平台/指定设备ID/标签解析可用设备列表 */
    public List<Device> resolveForTask(TestTask task) {
        Long teamId = task.getTeamId() != null ? task.getTeamId() : teamScope.scopeTeamId();
        List<Long> specifiedIds = parseDeviceIds(task.getDeviceIds());
        List<Device> candidates;
        if (!specifiedIds.isEmpty()) {
            candidates = deviceRepository.findAvailableByIds(specifiedIds, teamId);
        } else {
            int count = task.getParallelCount() != null ? task.getParallelCount() : 1;
            if (task.getPlatform() == TestTask.TaskPlatform.both) {
                int androidCount = (count + 1) / 2;
                int iosCount = count - androidCount;
                candidates = new ArrayList<>();
                candidates.addAll(deviceRepository.findAvailable(Device.Platform.android, teamId, PageRequest.of(0, androidCount * 3)));
                candidates.addAll(deviceRepository.findAvailable(Device.Platform.ios, teamId, PageRequest.of(0, iosCount * 3)));
            } else {
                Device.Platform p = Device.Platform.valueOf(task.getPlatform().name());
                candidates = new ArrayList<>(deviceRepository.findAvailable(p, teamId, PageRequest.of(0, count * 3)));
            }
        }
        return filterByTags(candidates, task.getDeviceTags(), task.getParallelCount() != null ? task.getParallelCount() : 1);
    }

    private List<Device> filterByTags(List<Device> devices, String requiredTags, int limit) {
        List<Device> sorted = devices.stream()
                .sorted(Comparator.comparingInt(d -> d.getFailCount() != null ? d.getFailCount() : 0))
                .toList();
        if (requiredTags == null || requiredTags.isBlank()) {
            return sorted.size() > limit ? sorted.subList(0, limit) : sorted;
        }
        List<String> tags = List.of(requiredTags.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        List<Device> matched = sorted.stream().filter(d -> matchesTags(d.getTags(), tags)).toList();
        if (matched.isEmpty()) {
            throw new AppException("NO_DEVICE", "无匹配标签的设备: " + requiredTags, HttpStatus.BAD_REQUEST);
        }
        return matched.size() > limit ? matched.subList(0, limit) : matched;
    }

    private boolean matchesTags(String deviceTags, List<String> required) {
        if (required.isEmpty()) return true;
        if (deviceTags == null || deviceTags.isBlank()) return false;
        List<String> owned = List.of(deviceTags.split(",")).stream().map(String::trim).map(String::toLowerCase).toList();
        return required.stream().map(String::toLowerCase).allMatch(owned::contains);
    }

    private static final int FAIL_ISOLATE_THRESHOLD = 3;

    public void recordExecutionOutcome(Long deviceId, boolean success) {
        deviceRepository.findById(deviceId).ifPresent(d -> {
            if (success) {
                if (d.getStatus() != Device.DeviceStatus.error) {
                    d.setFailCount(0);
                }
            } else {
                int c = (d.getFailCount() != null ? d.getFailCount() : 0) + 1;
                d.setFailCount(c);
                if (c >= FAIL_ISOLATE_THRESHOLD) {
                    d.setStatus(Device.DeviceStatus.error);
                }
            }
            deviceRepository.save(d);
        });
    }

    public Device updateTags(Long id, String tags) {
        Device device = getById(id);
        device.setTags(tags);
        return deviceRepository.save(device);
    }

    public void resetDeviceHealth(Long id) {
        Device device = getById(id);
        device.setFailCount(0);
        if (device.getStatus() == Device.DeviceStatus.error) {
            device.setStatus(Device.DeviceStatus.online);
        }
        deviceRepository.save(device);
    }

    public boolean renewLockDevice(Long deviceId, Long taskId, int ttlSeconds) {
        if (!lockService.renewLock(deviceId, taskId, ttlSeconds)) {
            return false;
        }
        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.setLockExpiresAt(LocalDateTime.now().plusSeconds(ttlSeconds));
            deviceRepository.save(device);
        });
        return true;
    }

    private List<Long> parseDeviceIds(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return Collections.emptyList();
        }
        String current = json.trim();
        for (int depth = 0; depth < 6; depth++) {
            try {
                return objectMapper.readValue(current, new TypeReference<List<Long>>() {});
            } catch (Exception ignored) {
            }
            try {
                String inner = objectMapper.readValue(current, String.class);
                if (inner == null || inner.isBlank() || inner.equals(current)) {
                    break;
                }
                current = inner.trim();
            } catch (Exception ignored) {
                break;
            }
        }
        return Collections.emptyList();
    }

    public boolean tryLockDevice(Long deviceId, Long taskId, int ttlSeconds) {
        if (tryLockDeviceOnce(deviceId, taskId, ttlSeconds)) {
            return true;
        }
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return false;
        Long holderTaskId = device.getLockedByTaskId();
        if (holderTaskId != null && isReclaimableLock(holderTaskId)) {
            releaseLock(deviceId);
            return tryLockDeviceOnce(deviceId, taskId, ttlSeconds);
        }
        return false;
    }

    private boolean tryLockDeviceOnce(Long deviceId, Long taskId, int ttlSeconds) {
        if (!lockService.tryLock(deviceId, taskId, ttlSeconds)) {
            return false;
        }
        try {
            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new AppException("NOT_FOUND", "设备不存在", HttpStatus.NOT_FOUND));
            device.setStatus(Device.DeviceStatus.busy);
            device.setLockedByTaskId(taskId);
            device.setLockExpiresAt(LocalDateTime.now().plusSeconds(ttlSeconds));
            deviceRepository.save(device);
            return true;
        } catch (RuntimeException e) {
            lockService.releaseLock(deviceId);
            throw e;
        }
    }

    private boolean isReclaimableLock(Long holderTaskId) {
        return taskRepository.findById(holderTaskId)
                .map(t -> t.getStatus() == TestTask.TaskStatus.failed
                        || t.getStatus() == TestTask.TaskStatus.success
                        || t.getStatus() == TestTask.TaskStatus.cancelled
                        || t.getStatus() == TestTask.TaskStatus.timeout
                        || isZombieRunningTask(t))
                .orElse(true);
    }

    private boolean isZombieRunningTask(TestTask task) {
        if (task.getStatus() != TestTask.TaskStatus.running) return false;
        if (task.getStartedAt() == null) return false;
        if (task.getStartedAt().isAfter(LocalDateTime.now().minusMinutes(1))) return false;
        return executionRepository.countByTaskId(task.getId()) == 0;
    }

    public void releaseLock(Long deviceId) {
        lockService.releaseLock(deviceId);
        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.setStatus(Device.DeviceStatus.online);
            device.setLockedByTaskId(null);
            device.setLockExpiresAt(null);
            deviceRepository.save(device);
        });
    }

    public void releaseLocksForTask(Long taskId) {
        if (taskId == null) return;
        for (Device device : deviceRepository.findByLockedByTaskId(taskId)) {
            releaseLock(device.getId());
        }
        lockService.releaseLocksForTask(taskId);
    }

    public void markOfflineStale(int thresholdSeconds) {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(thresholdSeconds);
        deviceRepository.findAll().forEach(d -> {
            if (d.getLastHeartbeatAt() != null && d.getLastHeartbeatAt().isBefore(cutoff)
                    && d.getStatus() != Device.DeviceStatus.maintenance) {
                d.setStatus(Device.DeviceStatus.offline);
                d.setLockedByTaskId(null);
                d.setLockExpiresAt(null);
                deviceRepository.save(d);
            }
        });
    }

    public void installApp(Long deviceId, String appPath) {
        Device device = getById(deviceId);
        executorClient.installApp(device.getSerialNumber(), device.getPlatform().name(), appPath);
    }

    public void uninstallApp(Long deviceId, String appPackage) {
        Device device = getById(deviceId);
        executorClient.uninstallApp(device.getSerialNumber(), device.getPlatform().name(), appPackage);
    }
}
