package com.atp.platform.service;

import com.atp.platform.dto.CreateTaskRequest;
import com.atp.platform.dto.JenkinsWebhookRequest;
import com.atp.platform.dto.UpdateCiConfigRequest;
import com.atp.platform.entity.CiConfig;
import com.atp.platform.entity.CiJobRecord;
import com.atp.platform.entity.TestCase;
import com.atp.platform.entity.TestReport;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.CiConfigRepository;
import com.atp.platform.repository.CiJobRecordRepository;
import com.atp.platform.repository.TestCaseRepository;
import com.atp.platform.repository.TestReportRepository;
import com.atp.platform.repository.TestTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CiService {

    private final CiConfigRepository configRepository;
    private final CiJobRecordRepository jobRecordRepository;
    private final TestTaskRepository taskRepository;
    private final TestReportRepository reportRepository;
    private final TaskService taskService;
    private final SchedulerService schedulerService;
    private final TestCaseRepository caseRepository;
    private final CheckpointService checkpointService;
    private final SuiteService suiteService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public CiConfig getConfig() {
        return configRepository.findFirstByOrderByIdAsc().orElseGet(this::initDefaultConfig);
    }

    public CiConfig updateConfig(UpdateCiConfigRequest req) {
        CiConfig config = getConfig();
        config.setWebhookToken(req.getWebhookToken());
        if (req.getEnabled() != null) config.setEnabled(req.getEnabled());
        if (req.getDefaultPlatform() != null) config.setDefaultPlatform(req.getDefaultPlatform());
        if (req.getDefaultScriptType() != null) config.setDefaultScriptType(req.getDefaultScriptType());
        if (req.getDefaultScriptContent() != null) config.setDefaultScriptContent(req.getDefaultScriptContent());
        if (req.getDefaultAppPackage() != null) config.setDefaultAppPackage(req.getDefaultAppPackage());
        if (req.getDefaultTimeoutSeconds() != null) config.setDefaultTimeoutSeconds(req.getDefaultTimeoutSeconds());
        if (req.getAutoSubmit() != null) config.setAutoSubmit(req.getAutoSubmit());
        if (req.getIpWhitelist() != null) config.setIpWhitelist(req.getIpWhitelist());
        if (req.getBranchAllow() != null) config.setBranchAllow(req.getBranchAllow());
        if (req.getBranchDeny() != null) config.setBranchDeny(req.getBranchDeny());
        if (req.getMaxConcurrentTasks() != null) config.setMaxConcurrentTasks(req.getMaxConcurrentTasks());
        if (req.getReceiveRetryCount() != null) config.setReceiveRetryCount(req.getReceiveRetryCount());
        if (req.getReceiveTimeoutSeconds() != null) config.setReceiveTimeoutSeconds(req.getReceiveTimeoutSeconds());
        config.setDefaultSuiteId(req.getDefaultSuiteId());
        config.setDefaultBaselineId(req.getDefaultBaselineId());
        config.setDefaultEnvId(req.getDefaultEnvId());
        return configRepository.save(config);
    }

    public Map<String, Object> handleJenkinsWebhook(JenkinsWebhookRequest req, String token, String clientIp) {
        CiConfig config = getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new AppException("CI_DISABLED", "CI 集成未启用", HttpStatus.FORBIDDEN);
        }
        if (token == null || !token.equals(config.getWebhookToken())) {
            throw new AppException("UNAUTHORIZED", "访问密钥无效", HttpStatus.FORBIDDEN);
        }
        assertIpAllowed(config, clientIp);
        assertBranchAllowed(config, req.getBranch());
        assertConcurrency(config);

        String payloadJson = toJsonSafe(req);
        Long suiteId = resolveSuiteId(req, config);
        Long caseId = longVal(req.getTask() != null ? req.getTask() : Map.of(), "case_id", null);

        try {
            if (suiteId != null) {
                return triggerSuite(req, config, suiteId, payloadJson);
            }
            return triggerTask(req, config, caseId, payloadJson);
        } catch (AppException e) {
            saveFailedRecord(req, payloadJson, e.getMessage());
            throw e;
        } catch (Exception e) {
            saveFailedRecord(req, payloadJson, e.getMessage());
            throw new AppException("CI_TRIGGER_FAILED", "触发失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> triggerSuite(JenkinsWebhookRequest req, CiConfig config, Long suiteId, String payloadJson) {
        CiJobRecord record = baseRecord(req, payloadJson);
        if (!Boolean.TRUE.equals(config.getAutoSubmit())) {
            record.setStatus("logged");
            record.setErrorMessage("自动提交任务已关闭，仅记录日志（套件 #" + suiteId + "）");
            jobRecordRepository.save(record);
            return Map.of(
                    "suite_id", suiteId,
                    "status", record.getStatus(),
                    "message", "仅记录日志，未执行回归"
            );
        }

        Map<String, Object> runResult = suiteService.runSuite(suiteId, 1L);
        Object runId = runResult.get("suite_run_id");
        if (runId != null) {
            record.setSuiteRunId(Long.valueOf(runId.toString()));
        }
        record.setStatus("running");
        jobRecordRepository.save(record);

        Map<String, Object> resp = new HashMap<>();
        resp.put("suite_run_id", runResult.get("suite_run_id"));
        resp.put("status", record.getStatus());
        resp.put("total", runResult.get("total"));
        resp.put("poll_url", "/api/v1/suite-runs/" + runResult.get("suite_run_id"));
        return resp;
    }

    private Map<String, Object> triggerTask(JenkinsWebhookRequest req, CiConfig config, Long caseId, String payloadJson) {
        if (!Boolean.TRUE.equals(config.getAutoSubmit())) {
            CiJobRecord record = baseRecord(req, payloadJson);
            record.setStatus("logged");
            record.setErrorMessage("自动提交任务已关闭，仅记录日志");
            jobRecordRepository.save(record);
            return Map.of(
                    "status", record.getStatus(),
                    "message", "仅记录日志，未执行回归"
            );
        }

        Map<String, Object> override = req.getTask() != null ? req.getTask() : Map.of();
        TestTask task;
        if (caseId != null) {
            TestCase testCase = caseRepository.findById(caseId)
                    .orElseThrow(() -> new AppException("NOT_FOUND", "用例不存在", HttpStatus.NOT_FOUND));
            Long pkgId = longVal(override, "app_package_id", null);
            task = checkpointService.createTaskFromCase(testCase, 1L, null, null, null, pkgId);
            Long envId = longVal(override, "env_id", config.getDefaultEnvId());
            if (envId != null) {
                task.setEnvId(envId);
            }
            if (override.containsKey("device_tags")) {
                task.setDeviceTags(str(override, "device_tags", task.getDeviceTags()));
            }
            if (override.containsKey("wait_template")) {
                task.setVariablesJson(mergeWaitTemplate(task.getVariablesJson(), str(override, "wait_template", "")));
            }
            mergeCiVariables(task, req);
            task = taskRepository.save(task);
        } else {
            CreateTaskRequest taskReq = buildTaskRequest(req, config);
            task = taskService.create(taskReq, null);
            if (override.containsKey("wait_template")) {
                task.setVariablesJson(mergeWaitTemplate(task.getVariablesJson(), str(override, "wait_template", "")));
            }
            mergeCiVariables(task, req);
            task = taskRepository.save(task);
        }

        CiJobRecord record = baseRecord(req, payloadJson);
        record.setTaskId(task.getId());
        record.setStatus("triggered");
        jobRecordRepository.save(record);

        taskService.submit(task.getId());
        schedulerService.enqueueTask(task.getId(), task.getPriority());
        record.setStatus("running");
        jobRecordRepository.save(record);

        return Map.of(
                "task_id", task.getId(),
                "status", record.getStatus(),
                "poll_url", "/api/v1/ci/tasks/" + task.getId() + "/status"
        );
    }

    public Map<String, Object> getTaskStatus(Long taskId) {
        TestTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        Map<String, Object> status = new HashMap<>();
        status.put("task_id", taskId);
        status.put("status", task.getStatus().name());
        status.put("error_code", task.getErrorCode());
        status.put("error_message", task.getErrorMessage());
        reportRepository.findByTaskId(taskId).ifPresent(report -> {
            status.put("pass_rate", report.getPassRate());
            status.put("success_count", report.getSuccessCount());
            status.put("failed_count", report.getFailedCount());
        });
        jobRecordRepository.findByTaskId(taskId).ifPresent(record -> {
            status.put("job_name", record.getJobName());
            status.put("build_number", record.getBuildNumber());
            status.put("ci_status", record.getStatus());
            status.put("branch", record.getBranch());
        });
        return status;
    }

    public List<CiJobRecord> recentJobs() {
        return jobRecordRepository.findTop20ByOrderByCreatedAtDesc();
    }

    public List<CiJobRecord> allJobs() {
        return jobRecordRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 任务完成后回调 Jenkins */
    public void notifyTaskCompleted(Long taskId) {
        jobRecordRepository.findByTaskId(taskId).ifPresent(record -> {
            if (record.getCallbackUrl() == null || record.getCallbackUrl().isBlank()) {
                return;
            }
            TestTask task = taskRepository.findById(taskId).orElse(null);
            if (task == null) return;

            TestReport report = reportRepository.findByTaskId(taskId).orElse(null);
            Map<String, Object> payload = new HashMap<>();
            payload.put("task_id", taskId);
            payload.put("status", task.getStatus().name());
            payload.put("job_name", record.getJobName());
            payload.put("build_number", record.getBuildNumber());
            payload.put("branch", record.getBranch());
            if (report != null) {
                payload.put("pass_rate", report.getPassRate());
                payload.put("success_count", report.getSuccessCount());
                payload.put("failed_count", report.getFailedCount());
            }

            try {
                var response = restClient.post()
                        .uri(record.getCallbackUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
                record.setCallbackStatus(response.getStatusCode().value());
                record.setCallbackResponse("OK");
                record.setStatus(task.getStatus() == TestTask.TaskStatus.success ? "success" : "failed");
            } catch (Exception e) {
                log.warn("Jenkins callback failed task={}: {}", taskId, e.getMessage());
                record.setCallbackStatus(0);
                record.setCallbackResponse(e.getMessage());
                record.setStatus("callback_failed");
            }
            record.setCallbackAt(LocalDateTime.now());
            jobRecordRepository.save(record);
        });
    }

    private CreateTaskRequest buildTaskRequest(JenkinsWebhookRequest req, CiConfig config) {
        CreateTaskRequest taskReq = new CreateTaskRequest();
        Map<String, Object> override = req.getTask() != null ? req.getTask() : Map.of();

        String jobLabel = req.getJobName() != null ? req.getJobName() : "jenkins";
        String buildLabel = req.getBuildNumber() != null ? "#" + req.getBuildNumber() : "";
        taskReq.setName(str(override, "name", "CI-" + jobLabel + buildLabel));
        taskReq.setDescription(str(override, "description", "Jenkins 触发: " + jobLabel + buildLabel));
        taskReq.setPlatform(str(override, "platform", config.getDefaultPlatform()));
        taskReq.setScriptType(str(override, "script_type", config.getDefaultScriptType()));
        taskReq.setScriptContent(str(override, "script_content", config.getDefaultScriptContent()));
        taskReq.setAppPackage(str(override, "app_package", config.getDefaultAppPackage()));
        String appPath = str(override, "app_path", req.getDownloadUrl());
        taskReq.setAppPath(appPath);
        taskReq.setTimeoutSeconds(intVal(override, "timeout_seconds", config.getDefaultTimeoutSeconds()));
        taskReq.setMaxRetries(intVal(override, "max_retries", 1));
        taskReq.setEnableRecording(boolVal(override, "enable_recording", true));
        taskReq.setPriority((byte) intVal(override, "priority", 3));
        taskReq.setParallelCount(intVal(override, "parallel_count", 1));
        Long envId = longVal(override, "env_id", config.getDefaultEnvId());
        if (envId != null) {
            taskReq.setEnvId(envId);
        }
        return taskReq;
    }

    private Long resolveSuiteId(JenkinsWebhookRequest req, CiConfig config) {
        Map<String, Object> override = req.getTask() != null ? req.getTask() : Map.of();
        Long fromPayload = longVal(override, "suite_id", null);
        if (fromPayload != null) return fromPayload;
        return config.getDefaultSuiteId();
    }

    private void mergeCiVariables(TestTask task, JenkinsWebhookRequest req) {
        try {
            Map<String, String> merged = new HashMap<>();
            if (task.getVariablesJson() != null && !task.getVariablesJson().isBlank()) {
                merged.putAll(objectMapper.readValue(task.getVariablesJson(), new TypeReference<Map<String, String>>() {}));
            }
            if (req.getBranch() != null) merged.put("CI_BRANCH", req.getBranch());
            if (req.getDownloadUrl() != null) merged.put("CI_DOWNLOAD_URL", req.getDownloadUrl());
            if (req.getJobName() != null) merged.put("CI_JOB_NAME", req.getJobName());
            if (req.getBuildNumber() != null) merged.put("CI_BUILD_NUM", req.getBuildNumber());
            task.setVariablesJson(objectMapper.writeValueAsString(merged));
        } catch (Exception ignored) {
            // keep existing variables
        }
    }

    private CiJobRecord baseRecord(JenkinsWebhookRequest req, String payloadJson) {
        CiJobRecord record = new CiJobRecord();
        record.setJobName(req.getJobName());
        record.setBuildNumber(req.getBuildNumber());
        record.setBranch(req.getBranch());
        record.setDownloadUrl(req.getDownloadUrl());
        record.setCallbackUrl(req.getCallbackUrl());
        record.setRequestPayload(payloadJson);
        return record;
    }

    private void saveFailedRecord(JenkinsWebhookRequest req, String payloadJson, String error) {
        CiJobRecord record = baseRecord(req, payloadJson);
        record.setStatus("failed");
        record.setErrorMessage(error);
        jobRecordRepository.save(record);
    }

    private void assertIpAllowed(CiConfig config, String clientIp) {
        String whitelist = config.getIpWhitelist();
        if (whitelist == null || whitelist.isBlank()) return;
        Set<String> allowed = splitList(whitelist);
        if (allowed.isEmpty()) return;
        if (clientIp == null || clientIp.isBlank()) {
            throw new AppException("IP_DENIED", "无法识别请求 IP，已拒绝", HttpStatus.FORBIDDEN);
        }
        String normalized = clientIp.replace("::ffff:", "");
        if (!allowed.contains(normalized) && !allowed.contains(clientIp)) {
            throw new AppException("IP_DENIED", "请求 IP 不在白名单内", HttpStatus.FORBIDDEN);
        }
    }

    private void assertBranchAllowed(CiConfig config, String branch) {
        String normalized = normalizeBranch(branch);
        Set<String> deny = splitList(config.getBranchDeny()).stream()
                .map(this::normalizeBranch)
                .collect(Collectors.toSet());
        if (!deny.isEmpty() && normalized != null && deny.contains(normalized)) {
            throw new AppException("BRANCH_DENIED", "分支已被禁止触发: " + branch, HttpStatus.FORBIDDEN);
        }
        Set<String> allow = splitList(config.getBranchAllow()).stream()
                .map(this::normalizeBranch)
                .collect(Collectors.toSet());
        if (!allow.isEmpty()) {
            if (normalized == null || !allow.contains(normalized)) {
                throw new AppException("BRANCH_DENIED", "分支不在允许列表中: " + branch, HttpStatus.FORBIDDEN);
            }
        }
    }

    private void assertConcurrency(CiConfig config) {
        Integer max = config.getMaxConcurrentTasks();
        if (max == null || max <= 0) return;
        long running = jobRecordRepository.countByStatusIn(List.of("running", "triggered", "building"));
        if (running >= max) {
            throw new AppException("CONCURRENCY_LIMIT", "CI 任务并发已达上限 (" + max + ")", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private Set<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split("[,\\n\\r]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String normalizeBranch(String branch) {
        if (branch == null) return null;
        String b = branch.trim();
        if (b.startsWith("refs/heads/")) b = b.substring("refs/heads/".length());
        if (b.startsWith("origin/")) b = b.substring("origin/".length());
        return b.isEmpty() ? null : b;
    }

    private String mergeWaitTemplate(String existingJson, String template) {
        if (template == null || template.isBlank()) return existingJson;
        try {
            Map<String, String> merged = new HashMap<>();
            if (existingJson != null && !existingJson.isBlank()) {
                merged.putAll(objectMapper.readValue(existingJson, new TypeReference<Map<String, String>>() {}));
            }
            merged.put("WAIT_TEMPLATE", template);
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            return "{\"WAIT_TEMPLATE\":\"" + template + "\"}";
        }
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private Long longVal(Map<String, Object> m, String key, Long def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private CiConfig initDefaultConfig() {
        CiConfig config = new CiConfig();
        config.setWebhookToken(UUID.randomUUID().toString().replace("-", ""));
        config.setEnabled(true);
        config.setDefaultScriptContent("print('CI triggered automation task')");
        config.setMaxConcurrentTasks(3);
        config.setReceiveRetryCount(0);
        config.setReceiveTimeoutSeconds(30);
        return configRepository.save(config);
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }

    private int intVal(Map<String, Object> m, String key, Integer def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        return def != null ? def : 0;
    }

    private boolean boolVal(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        return def;
    }
}
