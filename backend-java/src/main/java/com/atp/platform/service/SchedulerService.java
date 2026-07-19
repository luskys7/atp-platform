package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.*;
import com.atp.platform.event.TaskCompletedEvent;
import com.atp.platform.exception.AppException;
import com.atp.platform.exception.ErrorCodes;
import com.atp.platform.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final TaskQueueService taskQueue;
    private final AtpProperties properties;
    private final TestTaskRepository taskRepository;
    private final TaskExecutionRepository executionRepository;
    private final TestReportRepository reportRepository;
    private final RecordingResourceRepository recordingRepository;
    private final DeviceService deviceService;
    private final TaskService taskService;
    private final ReportService reportService;
    private final PythonExecutorClient executorClient;
    private final DataSetService dataSetService;
    private final Executor taskExecutor;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<CheckpointService> checkpointServiceProvider;
    private final ControlPoolService controlPoolService;
    private final DataFactoryService dataFactoryService;
    private final TestAccountService testAccountService;
    private final ManualInterventionService manualInterventionService;
    private final AppPackageService appPackageService;
    private final FailureSnapshotService failureSnapshotService;
    private final ExecutionStepMarkerService executionStepMarkerService;
    private final EnvironmentGovernanceService environmentGovernanceService;
    private final DefectLinkService defectLinkService;
    private final PlatformMessageService messageService;
    private final ExecutionLogRepository logRepository;

    public void enqueueTask(Long taskId, byte priority) throws AppException {
        if (taskQueue.size() >= properties.getScheduler().getQueueMaxSize()) {
            throw new AppException(ErrorCodes.E3001, HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            taskQueue.enqueue(objectMapper.writeValueAsString(new QueueItem(taskId, priority)));
        } catch (Exception e) {
            throw new AppException("QUEUE", "入队失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Scheduled(fixedDelay = 500)
    public void processQueue() {
        String item = taskQueue.dequeue();
        if (item == null) return;
        try {
            QueueItem queueItem = objectMapper.readValue(item, QueueItem.class);
            taskRepository.findById(queueItem.taskId()).ifPresent(this::dispatchTask);
        } catch (Exception e) {
            log.error("process queue error", e);
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void watchOfflineDevices() {
        deviceService.markOfflineStale(10);
    }

    private void dispatchTask(TestTask task) {
        task = taskRepository.findById(task.getId()).orElse(task);
        if (task.getStatus() == TestTask.TaskStatus.paused) {
            return;
        }
        if (task.getStatus() != TestTask.TaskStatus.queued && task.getStatus() != TestTask.TaskStatus.pending) {
            return;
        }
        try {
            appPackageService.assertAllowedForAutomation(task.getAppPackageId());
            environmentGovernanceService.assertTaskAllowed(task);
        } catch (AppException e) {
            failTask(task, e.getErrorCode() != null ? e.getErrorCode() : "GOVERNANCE", e.getMessage());
            return;
        }
        task.setStatus(TestTask.TaskStatus.running);
        task.setStartedAt(LocalDateTime.now());
        taskRepository.save(task);

        try {
            List<Device> devices = deviceService.resolveForTask(task);
            if (devices.isEmpty()) {
                failTask(task, ErrorCodes.E1002, "无可用设备");
                return;
            }

            int ttl = properties.getScheduler().getLockTtlSeconds();
            int locked = 0;
            for (Device device : devices) {
                if (deviceService.tryLockDevice(device.getId(), task.getId(), ttl)) {
                    TaskExecution exec = new TaskExecution();
                    exec.setTaskId(task.getId());
                    exec.setDeviceId(device.getId());
                    exec.setStatus(TestTask.TaskStatus.running);
                    exec.setStartedAt(LocalDateTime.now());
                    exec = executionRepository.save(exec);
                    locked++;
                    TestTask taskRef = taskRepository.findById(task.getId()).orElse(task);
                    TaskExecution finalExec = exec;
                    taskExecutor.execute(() -> runExecution(taskRef, finalExec, device));
                }
            }
            if (locked == 0) {
                failTask(task, ErrorCodes.E1002, ErrorCodes.message(ErrorCodes.E1002));
                deviceService.releaseLocksForTask(task.getId());
            }
        } catch (Exception e) {
            log.error("dispatch task {} failed", task.getId(), e);
            String msg = e instanceof AppException ae ? ae.getMessage() : "任务调度异常";
            failTask(task, ErrorCodes.E1002, msg);
        }
    }

    private void runExecution(TestTask task, TaskExecution exec, Device device) {
        AtomicBoolean done = new AtomicBoolean(false);
        int ttl = properties.getScheduler().getLockTtlSeconds();
        ScheduledExecutorService renewer = Executors.newSingleThreadScheduledExecutor();
        renewer.scheduleAtFixedRate(() -> {
            if (!done.get()) {
                deviceService.renewLockDevice(device.getId(), task.getId(), ttl);
            }
        }, ttl / 2L, ttl / 2L, TimeUnit.SECONDS);

        try {
            taskService.saveLog(task.getId(), exec.getId(), device.getId(), "script", "info",
                    "开始执行脚本 [" + task.getScriptType() + "] 于设备 " + device.getSerialNumber());

            if (task.getDatasetId() != null && task.getDatasetRowId() == null) {
                try {
                    DataSetRow row = dataSetService.acquireRow(task.getDatasetId(), task.getId());
                    task.setDatasetRowId(row.getId());
                    taskRepository.save(task);
                    taskService.saveLog(task.getId(), exec.getId(), device.getId(), "platform", "info",
                            "已锁定数据集行 #" + row.getId());
                } catch (Exception e) {
                    exec.setStatus(TestTask.TaskStatus.failed);
                    exec.setFinishedAt(LocalDateTime.now());
                    exec.setErrorCode(ErrorCodes.E3002);
                    exec.setErrorMessage("数据集锁定失败: " + e.getMessage());
                    executionRepository.save(exec);
                    taskService.saveLog(task.getId(), exec.getId(), device.getId(), "platform", "error",
                            exec.getErrorMessage());
                    return;
                }
            }

            if (Boolean.TRUE.equals(task.getUseAccountPool()) && task.getTestAccountId() == null) {
                try {
                    TestAccount account = testAccountService.acquireForTask(
                            task.getId(), task.getEnvId(), task.getDeviceTags());
                    task.setTestAccountId(account.getId());
                    taskRepository.save(task);
                    taskService.saveLog(task.getId(), exec.getId(), device.getId(), "platform", "info",
                            "已占用测试账号: " + account.getUsername());
                } catch (Exception e) {
                    exec.setStatus(TestTask.TaskStatus.failed);
                    exec.setFinishedAt(LocalDateTime.now());
                    exec.setErrorCode(ErrorCodes.E3002);
                    exec.setErrorMessage("测试账号占用失败: " + e.getMessage());
                    executionRepository.save(exec);
                    taskService.saveLog(task.getId(), exec.getId(), device.getId(), "platform", "error",
                            exec.getErrorMessage());
                    return;
                }
            }

            int timeout = task.getTimeoutSeconds() != null ? task.getTimeoutSeconds() : 3600;
            CompletableFuture<PythonExecutorClient.ExecutorResult> future = CompletableFuture.supplyAsync(
                    () -> executorClient.execute(task, exec, device));

            PythonExecutorClient.ExecutorResult result;
            try {
                result = future.get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                handleTimeout(task, exec, device);
                return;
            }

            persistExecutorLogs(task, exec, device, result);

            Long recordingId = null;
            if (task.getEnableRecording() != null && task.getEnableRecording() && result.recordingPath() != null) {
                recordingId = saveRecording(task, exec, device, result);
            }
            executionStepMarkerService.ingestFromLogs(
                    task.getId(), exec.getId(), device.getId(), recordingId, result.logs());

            exec.setFinishedAt(LocalDateTime.now());
            if (result.success()) {
                exec.setStatus(TestTask.TaskStatus.success);
                exec.setResultSummary(result.summary());
                taskService.saveLog(task.getId(), exec.getId(), device.getId(), "script", "info", "脚本执行完成");
                deviceService.recordExecutionOutcome(device.getId(), true);
            } else {
                Optional<ManualWaitSignal> manualWait = parseManualWait(result.logs(), result.errorMessage());
                if (manualWait.isPresent()) {
                    exec.setStatus(TestTask.TaskStatus.waiting_manual);
                    exec.setFinishedAt(LocalDateTime.now());
                    exec.setErrorMessage("等待人工介入: " + manualWait.get().prompt());
                    executionRepository.save(exec);
                    task.setStatus(TestTask.TaskStatus.waiting_manual);
                    taskRepository.save(task);
                    manualInterventionService.create(task.getId(), exec.getId(),
                            manualWait.get().stepIndex(), manualWait.get().prompt());
                    taskService.saveLog(task.getId(), exec.getId(), device.getId(), "platform", "warn",
                            "步骤 #" + manualWait.get().stepIndex() + " 触发人工介入");
                    deviceService.recordExecutionOutcome(device.getId(), true);
                    return;
                }
                exec.setStatus(TestTask.TaskStatus.failed);
                exec.setErrorCode(ErrorCodes.E3002);
                exec.setErrorMessage(result.errorMessage());
                taskService.saveLog(task.getId(), exec.getId(), device.getId(), "script", "error", result.errorMessage());
                if (result.logs() != null) {
                    failureSnapshotService.ingestFromLogs(task.getId(), exec.getId(), device.getId(),
                            result.logs().stream().map(PythonExecutorClient.LogEntry::message).toList());
                }
                deviceService.recordExecutionOutcome(device.getId(), false);
                checkpointServiceProvider.getObject().recordExecutionFailure(task.getId(), exec.getId(), result.errorMessage(), result.logs());
                tryRetry(task, exec);
            }
            executionRepository.save(exec);
        } catch (Exception e) {
            exec.setStatus(TestTask.TaskStatus.failed);
            exec.setFinishedAt(LocalDateTime.now());
            exec.setErrorMessage(e.getMessage());
            executionRepository.save(exec);
            log.error("execution error task={} exec={}", task.getId(), exec.getId(), e);
        } finally {
            done.set(true);
            renewer.shutdownNow();
            deviceService.releaseLock(device.getId());
            TestTask latest = taskRepository.findById(task.getId()).orElse(task);
            if (latest.getDatasetRowId() != null) {
                dataSetService.releaseRow(latest.getDatasetRowId());
                latest.setDatasetRowId(null);
                taskRepository.save(latest);
            }
            if (latest.getStatus() != TestTask.TaskStatus.waiting_manual) {
                testAccountService.releaseByTaskId(task.getId());
            }
            TestTask latestForComplete = taskRepository.findById(task.getId()).orElse(latest);
            if (latestForComplete.getStatus() != TestTask.TaskStatus.waiting_manual) {
                checkTaskCompletion(task.getId());
            }
        }
    }

    private record ManualWaitSignal(int stepIndex, String prompt) {}

    private static final java.util.regex.Pattern MANUAL_WAIT_PATTERN =
            java.util.regex.Pattern.compile("ATP_MANUAL_WAIT:step=(\\d+)(?::prompt=(.*))?$");

    private Optional<ManualWaitSignal> parseManualWait(List<PythonExecutorClient.LogEntry> logs, String errorMessage) {
        if (logs != null) {
            for (PythonExecutorClient.LogEntry entry : logs) {
                Optional<ManualWaitSignal> s = matchManualWaitLine(entry.message());
                if (s.isPresent()) return s;
            }
        }
        return matchManualWaitLine(errorMessage);
    }

    private Optional<ManualWaitSignal> matchManualWaitLine(String line) {
        if (line == null) return Optional.empty();
        var m = MANUAL_WAIT_PATTERN.matcher(line.trim());
        if (!m.find()) return Optional.empty();
        int step = Integer.parseInt(m.group(1));
        String prompt = m.group(2) != null ? m.group(2).trim() : "请人工处理后继续";
        return Optional.of(new ManualWaitSignal(step, prompt));
    }

    private void handleTimeout(TestTask task, TaskExecution exec, Device device) {
        exec.setStatus(TestTask.TaskStatus.timeout);
        exec.setFinishedAt(LocalDateTime.now());
        exec.setErrorCode(ErrorCodes.E3002);
        exec.setErrorMessage(ErrorCodes.message(ErrorCodes.E3002));
        executionRepository.save(exec);
        taskService.saveLog(task.getId(), exec.getId(), device.getId(), "platform", "error",
                ErrorCodes.message(ErrorCodes.E3002));
    }

    private void persistExecutorLogs(TestTask task, TaskExecution exec, Device device,
                                   PythonExecutorClient.ExecutorResult result) {
        if (result.logs() == null) return;
        Map<String, String> outputVars = new LinkedHashMap<>();
        for (PythonExecutorClient.LogEntry entry : result.logs()) {
            taskService.saveLog(task.getId(), exec.getId(), device.getId(),
                    entry.logType(), entry.level(), entry.message());
            parseVarOutput(entry.message(), outputVars);
            ingestAutoCollectedControls(task, entry.message());
            ingestDataFactoryOutput(task, entry.message());
        }
        mergeTaskOutputVars(task, outputVars);
    }

    private static final java.util.regex.Pattern CONTROLS_JSON_PATTERN =
            java.util.regex.Pattern.compile("ATP_CONTROLS_JSON:(.+)");

    @SuppressWarnings("unchecked")
    private void ingestAutoCollectedControls(TestTask task, String message) {
        if (message == null || task.getAppPackage() == null) return;
        var m = CONTROLS_JSON_PATTERN.matcher(message);
        if (!m.find()) return;
        try {
            List<Map<String, Object>> items = objectMapper.readValue(m.group(1),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            int added = controlPoolService.ingestAutoCollected(task.getAppPackage(), items, task.getCreatedBy());
            if (added > 0) {
                log.info("Auto-collected {} controls for task={} app={}", added, task.getId(), task.getAppPackage());
            }
        } catch (Exception e) {
            log.warn("parse auto-collected controls failed task={}", task.getId());
        }
    }

    private static final java.util.regex.Pattern FACTORY_OUT_PATTERN =
            java.util.regex.Pattern.compile("ATP_FACTORY_OUT:(.+)");

    private void ingestDataFactoryOutput(TestTask task, String message) {
        if (message == null) return;
        var m = FACTORY_OUT_PATTERN.matcher(message);
        if (!m.find()) return;
        try {
            JsonNode node = objectMapper.readTree(m.group(1));
            long templateId = node.path("template_id").asLong();
            String varsJson = node.has("vars") ? objectMapper.writeValueAsString(node.get("vars")) : "{}";
            dataFactoryService.ingestFactoryOutput(task.getId(), task.getSuiteRunId(), templateId, varsJson);
        } catch (Exception e) {
            log.warn("parse data factory output failed task={}", task.getId());
        }
    }

    private static final java.util.regex.Pattern VAR_OUT_PATTERN =
            java.util.regex.Pattern.compile("ATP_VAR_OUT:([^=\\s]+)=(.*)");

    private void parseVarOutput(String message, Map<String, String> vars) {
        if (message == null) return;
        var m = VAR_OUT_PATTERN.matcher(message);
        if (m.find()) {
            vars.put(m.group(1), m.group(2));
        }
    }

    private void mergeTaskOutputVars(TestTask task, Map<String, String> outputVars) {
        if (outputVars.isEmpty()) return;
        try {
            Map<String, String> merged = new LinkedHashMap<>();
            if (task.getVariablesJson() != null && !task.getVariablesJson().isBlank()) {
                merged.putAll(objectMapper.readValue(task.getVariablesJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}));
            }
            merged.putAll(outputVars);
            task.setVariablesJson(objectMapper.writeValueAsString(merged));
            taskRepository.save(task);
        } catch (Exception e) {
            log.warn("merge output vars failed task={}", task.getId());
        }
    }

    private Long saveRecording(TestTask task, TaskExecution exec, Device device,
                               PythonExecutorClient.ExecutorResult result) {
        RecordingResource rec = new RecordingResource();
        rec.setTaskId(task.getId());
        rec.setExecutionId(exec.getId());
        rec.setDeviceId(device.getId());
        rec.setFileName("task_" + task.getId() + "_exec_" + exec.getId() + ".mp4");
        rec.setFilePath(result.recordingPath());
        rec.setFileSize(result.fileSize());
        rec.setWatermarkHash(result.watermarkHash() != null ? result.watermarkHash() : "");
        if (result.operatorLabel() != null && !result.operatorLabel().isBlank()) {
            rec.setOperatorLabel(result.operatorLabel());
        }
        rec.setFormat("mp4");
        rec.setStatus(RecordingResource.RecordingStatus.ready);
        rec = recordingRepository.save(rec);
        return rec.getId();
    }

    private void tryRetry(TestTask task, TaskExecution exec) {
        int maxRetries = task.getMaxRetries() != null ? task.getMaxRetries() : 0;
        int retryCount = task.getRetryCount() != null ? task.getRetryCount() : 0;
        if (retryCount < maxRetries) {
            task.setRetryCount(retryCount + 1);
            task.setStatus(TestTask.TaskStatus.queued);
            taskRepository.save(task);
            try {
                enqueueTask(task.getId(), task.getPriority());
            } catch (AppException e) {
                log.warn("retry enqueue failed task={}", task.getId());
            }
        }
    }

    private void checkTaskCompletion(Long taskId) {
        if (executionRepository.countByTaskIdAndStatus(taskId, TestTask.TaskStatus.running) > 0) {
            return;
        }
        List<TaskExecution> executions = executionRepository.findByTaskId(taskId);
        List<String> envCodes = properties.getGovernance().getEnvErrorCodes();

        int success = (int) executions.stream()
                .filter(e -> e.getStatus() == TestTask.TaskStatus.success).count();
        int envFailed = (int) executions.stream()
                .filter(e -> isEnvFailure(e, envCodes)).count();
        int businessFailed = (int) executions.stream()
                .filter(e -> e.getStatus() != TestTask.TaskStatus.success
                        && e.getStatus() != TestTask.TaskStatus.waiting_manual
                        && !isEnvFailure(e, envCodes)).count();

        TestTask task = taskRepository.findById(taskId).orElseThrow();
        if (task.getStatus() == TestTask.TaskStatus.queued
                || task.getStatus() == TestTask.TaskStatus.waiting_manual) {
            return;
        }

        int effectiveTotal = success + businessFailed;
        double passRate = effectiveTotal > 0 ? (double) success / effectiveTotal * 100 : 100;
        double minPassRate = properties.getGovernance().getMinPassRate();

        if (passRate < minPassRate && businessFailed > 0) {
            int retryCount = task.getRetryCount() != null ? task.getRetryCount() : 0;
            int maxRetries = task.getMaxRetries() != null ? task.getMaxRetries() : 0;
            if (retryCount < maxRetries) {
                task.setRetryCount(retryCount + 1);
                task.setStatus(TestTask.TaskStatus.queued);
                taskRepository.save(task);
                taskService.saveLog(taskId, null, null, "platform", "warn",
                        String.format("通过率 %.2f%% 低于阈值 %.2f%%，触发第 %d 次重试", passRate, minPassRate, retryCount + 1));
                try {
                    enqueueTask(taskId, task.getPriority());
                } catch (AppException e) {
                    log.warn("pass-rate retry enqueue failed task={}", taskId);
                }
                return;
            }
        }

        task.setStatus(businessFailed > 0 ? TestTask.TaskStatus.failed : TestTask.TaskStatus.success);
        task.setFinishedAt(LocalDateTime.now());
        taskRepository.save(task);

        TestReport report = reportRepository.findByTaskId(taskId).orElse(new TestReport());
        report.setTaskId(taskId);
        report.setTitle(task.getName() + " - 测试报告");
        report.setTotalExecutions(executions.size());
        report.setSuccessCount(success);
        report.setFailedCount(businessFailed);
        report.setPassRate(passRate);
        String summary = String.format("共 %d 次执行，成功 %d，业务失败 %d", executions.size(), success, businessFailed);
        if (envFailed > 0) {
            summary += String.format("，环境异常 %d（已剔除统计）", envFailed);
        }
        summary += String.format("，通过率 %.2f%%", passRate);
        if (passRate < minPassRate) {
            summary += String.format(" [低于阈值 %.2f%%]", minPassRate);
        }
        report.setSummary(summary);
        reportService.enrichFromTask(report, task);
        reportRepository.save(report);
        List<String> logMessages = logRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(ExecutionLog::getMessage).toList();
        defectLinkService.onTaskFinished(task, report, logMessages);
        if (businessFailed > 0 && task.getCreatedBy() != null) {
            messageService.send(task.getCreatedBy(), "任务执行失败",
                    "任务「" + task.getName() + "」执行失败，通过率 " + String.format("%.2f", passRate) + "%",
                    "task_failed", taskId, null);
        }
        try {
            int cleaned = dataFactoryService.cleanupForTask(taskId);
            if (cleaned > 0) {
                taskService.saveLog(taskId, null, null, "platform", "info",
                        "脏数据清理完成: " + cleaned + " 条");
            }
        } catch (Exception e) {
            log.warn("data factory cleanup failed task={}: {}", taskId, e.getMessage());
        }
        checkpointServiceProvider.getObject().onTaskFinished(taskId, businessFailed == 0);
        eventPublisher.publishEvent(new TaskCompletedEvent(taskId));
    }

    private boolean isEnvFailure(TaskExecution exec, List<String> envCodes) {
        return exec.getErrorCode() != null && envCodes.contains(exec.getErrorCode());
    }

    public void cancelTask(Long taskId) {
        TestTask task = taskRepository.findById(taskId).orElseThrow();
        if (task.getStatus() == TestTask.TaskStatus.running) {
            executionRepository.findByTaskIdAndStatus(taskId, TestTask.TaskStatus.running)
                    .forEach(e -> {
                        deviceService.releaseLock(e.getDeviceId());
                        e.setStatus(TestTask.TaskStatus.cancelled);
                        executionRepository.save(e);
                    });
        }
        task.setStatus(TestTask.TaskStatus.cancelled);
        task.setFinishedAt(LocalDateTime.now());
        taskRepository.save(task);
        deviceService.releaseLocksForTask(taskId);
        testAccountService.releaseByTaskId(taskId);
    }

    public void pauseTask(Long taskId) {
        TestTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        if (task.getStatus() != TestTask.TaskStatus.queued) {
            throw new AppException("INVALID", "仅排队中任务可暂停", HttpStatus.BAD_REQUEST);
        }
        task.setStatus(TestTask.TaskStatus.paused);
        taskRepository.save(task);
        taskService.saveLog(taskId, null, null, "platform", "info", "任务已暂停");
    }

    public void resumeTask(Long taskId) throws AppException {
        TestTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        if (task.getStatus() != TestTask.TaskStatus.paused) {
            throw new AppException("INVALID", "仅暂停任务可恢复", HttpStatus.BAD_REQUEST);
        }
        task.setStatus(TestTask.TaskStatus.queued);
        taskRepository.save(task);
        enqueueTask(taskId, task.getPriority() != null ? task.getPriority() : (byte) 5);
        taskService.saveLog(taskId, null, null, "platform", "info", "任务已恢复排队");
    }

    public void pinTask(Long taskId) throws AppException {
        TestTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        if (task.getStatus() != TestTask.TaskStatus.queued
                && task.getStatus() != TestTask.TaskStatus.paused
                && task.getStatus() != TestTask.TaskStatus.pending) {
            throw new AppException("INVALID", "当前状态不可置顶", HttpStatus.BAD_REQUEST);
        }
        task.setPriority((byte) 0);
        if (task.getStatus() == TestTask.TaskStatus.paused) {
            task.setStatus(TestTask.TaskStatus.queued);
        } else if (task.getStatus() == TestTask.TaskStatus.pending) {
            task.setStatus(TestTask.TaskStatus.queued);
        }
        taskRepository.save(task);
        enqueueTask(taskId, (byte) 0);
        taskService.saveLog(taskId, null, null, "platform", "info", "任务已置顶（priority=0）");
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanupZombieTasks() {
        for (TestTask task : taskRepository.findByStatusOrderByStartedAtDesc(TestTask.TaskStatus.running)) {
            if (task.getStartedAt() == null) continue;
            if (task.getStartedAt().isAfter(LocalDateTime.now().minusSeconds(45))) continue;
            if (executionRepository.countByTaskId(task.getId()) == 0) {
                log.warn("cleanup zombie task id={}", task.getId());
                failTask(task, ErrorCodes.E1002, "任务调度异常，已自动释放设备锁");
            }
        }
    }

    private void failTask(TestTask task, String errorCode, String message) {
        task.setStatus(TestTask.TaskStatus.failed);
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorCode(errorCode);
        task.setErrorMessage(message);
        taskRepository.save(task);
        deviceService.releaseLocksForTask(task.getId());
        testAccountService.releaseByTaskId(task.getId());
    }

    private record QueueItem(Long taskId, byte priority) {}
}
