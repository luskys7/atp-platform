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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        return configRepository.save(config);
    }

    public Map<String, Object> handleJenkinsWebhook(JenkinsWebhookRequest req, String token) {
        CiConfig config = getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new AppException("CI_DISABLED", "CI 集成未启用", HttpStatus.FORBIDDEN);
        }
        if (token == null || !token.equals(config.getWebhookToken())) {
            throw new AppException("UNAUTHORIZED", "Webhook Token 无效", HttpStatus.UNAUTHORIZED);
        }

        Map<String, Object> override = req.getTask() != null ? req.getTask() : Map.of();
        Long suiteId = longVal(override, "suite_id", null);
        Long caseId = longVal(override, "case_id", null);

        if (suiteId != null) {
            Map<String, Object> runResult = suiteService.runSuite(suiteId, 1L);
            CiJobRecord record = new CiJobRecord();
            record.setTaskId(null);
            record.setJobName(req.getJobName());
            record.setBuildNumber(req.getBuildNumber());
            record.setCallbackUrl(req.getCallbackUrl());
            record.setStatus("running");
            jobRecordRepository.save(record);
            Map<String, Object> resp = new HashMap<>();
            resp.put("suite_run_id", runResult.get("suite_run_id"));
            resp.put("status", record.getStatus());
            resp.put("total", runResult.get("total"));
            resp.put("poll_url", "/api/v1/suite-runs/" + runResult.get("suite_run_id"));
            return resp;
        }

        TestTask task;
        if (caseId != null) {
            TestCase testCase = caseRepository.findById(caseId)
                    .orElseThrow(() -> new AppException("NOT_FOUND", "用例不存在", HttpStatus.NOT_FOUND));
            Long pkgId = longVal(override, "app_package_id", null);
            task = checkpointService.createTaskFromCase(testCase, 1L, null, null, null, pkgId);
            if (override.containsKey("env_id") && override.get("env_id") != null) {
                task.setEnvId(Long.valueOf(override.get("env_id").toString()));
            }
            if (override.containsKey("device_tags")) {
                task.setDeviceTags(str(override, "device_tags", task.getDeviceTags()));
            }
            if (override.containsKey("wait_template")) {
                task.setVariablesJson(mergeWaitTemplate(task.getVariablesJson(), str(override, "wait_template", "")));
            }
            task = taskRepository.save(task);
        } else {
            CreateTaskRequest taskReq = buildTaskRequest(req, config);
            task = taskService.create(taskReq, null);
            if (override.containsKey("wait_template")) {
                task.setVariablesJson(mergeWaitTemplate(task.getVariablesJson(), str(override, "wait_template", "")));
                task = taskRepository.save(task);
            }
        }

        CiJobRecord record = new CiJobRecord();
        record.setTaskId(task.getId());
        record.setJobName(req.getJobName());
        record.setBuildNumber(req.getBuildNumber());
        record.setCallbackUrl(req.getCallbackUrl());
        record.setStatus("triggered");
        jobRecordRepository.save(record);

        if (Boolean.TRUE.equals(config.getAutoSubmit())) {
            taskService.submit(task.getId());
            schedulerService.enqueueTask(task.getId(), task.getPriority());
            record.setStatus("running");
            jobRecordRepository.save(record);
        }

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
        });
        return status;
    }

    public List<CiJobRecord> recentJobs() {
        return jobRecordRepository.findTop20ByOrderByCreatedAtDesc();
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
        taskReq.setAppPath(str(override, "app_path", null));
        taskReq.setTimeoutSeconds(intVal(override, "timeout_seconds", config.getDefaultTimeoutSeconds()));
        taskReq.setMaxRetries(intVal(override, "max_retries", 1));
        taskReq.setEnableRecording(boolVal(override, "enable_recording", true));
        taskReq.setPriority((byte) intVal(override, "priority", 3));
        taskReq.setParallelCount(intVal(override, "parallel_count", 1));
        if (override.containsKey("env_id") && override.get("env_id") != null) {
            taskReq.setEnvId(Long.valueOf(override.get("env_id").toString()));
        }
        return taskReq;
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
