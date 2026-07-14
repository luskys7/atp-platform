package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.Device;
import com.atp.platform.entity.TaskExecution;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.AppPackageRepository;
import com.atp.platform.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PythonExecutorClient {

    private final AtpProperties properties;
    private final ControlResolverService controlResolverService;
    private final VisualScriptGenerator visualScriptGenerator;
    private final AppPackageRepository appPackageRepository;
    private final VariableContextService variableContextService;
    private final ExecutorPoolService executorPoolService;
    private final AssertPolicyService assertPolicyService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private RestClient restClient;

    @PostConstruct
    void initRestClient() {
        this.restClient = restClientBuilder.build();
    }

    public ExecutorResult execute(TestTask task, TaskExecution execution, Device device) {
        ExecutorRequest req = buildRequest(task, execution, device);
        List<String> urls = executorPoolService.healthyUrls();
        Exception lastError = null;
        String lastUrl = null;
        for (String baseUrl : urls) {
            if (lastUrl != null) {
                executorPoolService.recordFailover(lastUrl, baseUrl, task.getId(), execution.getId(), "failover");
            }
            lastUrl = baseUrl;
            try {
                String jsonBody = objectMapper.writeValueAsString(req);
                String respBody = restClient.post()
                        .uri(baseUrl + "/api/v1/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(jsonBody)
                        .retrieve()
                        .body(String.class);
                ExecutorResponse resp = respBody != null ? objectMapper.readValue(respBody, ExecutorResponse.class) : null;
                if (resp == null) {
                    lastError = new RuntimeException("执行器无响应");
                    executorPoolService.markUnhealthy(baseUrl);
                    continue;
                }
                executorPoolService.markHealthy(baseUrl);
                List<LogEntry> logs = parseLogs(resp.getLogs());
                if (resp.isSuccess()) {
                    return ExecutorResult.success(resp.getSummary(), resp.getRecordingPath(),
                            resp.getWatermarkHash(), resp.getFileSize() != null ? resp.getFileSize() : 0L,
                            logs, resp.getOperatorLabel());
                }
                return ExecutorResult.failed(resp.getErrorMessage(), logs);
            } catch (Exception e) {
                lastError = e;
                log.warn("executor {} failed: {}", baseUrl, e.getMessage());
                executorPoolService.markUnhealthy(baseUrl);
            }
        }
        log.error("all executors failed");
        return ExecutorResult.failed("执行器调用失败: " + (lastError != null ? lastError.getMessage() : "无可用节点"));
    }

    public void installApp(String serialNumber, String platform, String appPath) {
        postJson("/api/v1/device/install", Map.of(
                "serial_number", serialNumber,
                "platform", platform,
                "app_path", appPath
        ));
    }

    public void uninstallApp(String serialNumber, String platform, String appPackage) {
        postJson("/api/v1/device/uninstall", Map.of(
                "serial_number", serialNumber,
                "platform", platform,
                "app_package", appPackage
        ));
    }

    public void tap(String serialNumber, int x, int y) {
        postJson("/api/v1/device/tap", Map.of(
                "serial_number", serialNumber,
                "x", x,
                "y", y
        ));
    }

    public void swipe(String serialNumber, int x1, int y1, int x2, int y2, int durationMs) {
        postJson("/api/v1/device/swipe", Map.of(
                "serial_number", serialNumber,
                "x1", x1, "y1", y1, "x2", x2, "y2", y2, "duration_ms", durationMs
        ));
    }

    public void inputText(String serialNumber, String text, Integer focusX, Integer focusY) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("serial_number", serialNumber);
        body.put("text", text != null ? text : "");
        if (focusX != null) body.put("focus_x", focusX);
        if (focusY != null) body.put("focus_y", focusY);
        postJson("/api/v1/device/input-text", body);
    }

    public void pressSystemKey(String serialNumber, String key) {
        postJson("/api/v1/device/system-key", Map.of(
                "serial_number", serialNumber,
                "key", key != null ? key : ""
        ));
    }

    private void postJson(String path, Map<String, Object> payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            restClient.post()
                    .uri(executorUrl(path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw executorError(e);
        } catch (Exception e) {
            throw new AppException(
                    "EXECUTOR_ERROR",
                    "执行器不可用: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJsonForMap(String path, Map<String, Object> payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            Map<?, ?> resp = restClient.post()
                    .uri(executorUrl(path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null) {
                return Map.of();
            }
            Map<String, Object> out = new java.util.HashMap<>();
            resp.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        } catch (RestClientResponseException e) {
            throw executorError(e);
        } catch (Exception e) {
            throw new AppException(
                    "EXECUTOR_ERROR",
                    "执行器不可用: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private AppException executorError(RestClientResponseException e) {
        String detail = e.getResponseBodyAsString(StandardCharsets.UTF_8);
        String msg = detail;
        try {
            if (detail != null && detail.trim().startsWith("{")) {
                Map<?, ?> json = objectMapper.readValue(detail, Map.class);
                if (json.get("detail") != null) {
                    msg = String.valueOf(json.get("detail"));
                }
            }
        } catch (Exception ignored) {
            // keep raw body
        }
        if (msg == null || msg.isBlank()) {
            msg = e.getMessage();
        }
        log.warn("executor call failed status={} body={}", e.getStatusCode().value(), detail);
        HttpStatus status = e.getStatusCode().value() == 400
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;
        return new AppException("EXECUTOR_ERROR", msg, status);
    }

    private String executorUrl(String path) {
        return properties.getExecutor().getUrl() + path;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listUsbDevices() {
        try {
            Map<?, ?> resp = restClient.get()
                    .uri(properties.getExecutor().getUrl() + "/api/v1/device/adb-list")
                    .retrieve()
                    .body(Map.class);
            if (resp == null) {
                return Map.of("success", false, "message", "执行器无响应", "devices", List.of());
            }
            Map<String, Object> out = new java.util.HashMap<>();
            resp.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        } catch (Exception e) {
            log.warn("listUsbDevices failed: {}", e.getMessage());
            return Map.of("success", false, "message", "执行器不可用: " + e.getMessage(), "devices", List.of());
        }
    }

    public void warmUiCache(String serialNumber, String platform) {
        warmUiCache(serialNumber, platform, false);
    }

    public void warmUiCache(String serialNumber, String platform, boolean blocking) {
        try {
            postJsonForMap("/api/v1/device/warm-ui-cache", Map.of(
                    "serial_number", serialNumber,
                    "platform", platform != null ? platform : "android",
                    "blocking", blocking
            ));
        } catch (Exception e) {
            log.warn("warmUiCache failed serial={}: {}", serialNumber, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> validateLocatorOnScreen(String serialNumber, String platform,
                                                      Map<String, Object> locators,
                                                      List<Map<String, Object>> locatorChain) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("serial_number", serialNumber);
            body.put("platform", platform != null ? platform : "android");
            body.put("locators", locators != null ? locators : Map.of());
            body.put("locator_chain", locatorChain != null ? locatorChain : List.of());
            Map<String, Object> resp = postJsonForMap("/api/v1/device/validate-locator", body);
            if (resp.isEmpty()) {
                return Map.of("valid", false, "error", "executor_unavailable");
            }
            return resp;
        } catch (Exception e) {
            log.warn("validateLocatorOnScreen failed serial={}: {}", serialNumber, e.getMessage());
            return Map.of("valid", false, "error", "executor_unavailable");
        }
    }

    public Map<String, Object> validateLocatorOnScreen(String serialNumber, String platform,
                                                      Map<String, Object> locators) {
        return validateLocatorOnScreen(serialNumber, platform, locators, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> inspectPoint(String serialNumber, String platform, int x, int y,
                                           Integer displayWidth, Integer displayHeight) {
        return inspectPoint(serialNumber, platform, x, y, displayWidth, displayHeight, false);
    }

    public Map<String, Object> inspectPoint(String serialNumber, String platform, int x, int y,
                                           Integer displayWidth, Integer displayHeight, boolean blocking) {
        return inspectPoint(serialNumber, platform, x, y, displayWidth, displayHeight, blocking, null, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> inspectPoint(String serialNumber, String platform, int x, int y,
                                           Integer displayWidth, Integer displayHeight, boolean blocking,
                                           String agentHost, Integer wdaPort) {
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("serial_number", serialNumber);
            body.put("platform", platform);
            body.put("x", x);
            body.put("y", y);
            body.put("blocking", blocking);
            if (displayWidth != null && displayWidth > 0) body.put("display_width", displayWidth);
            if (displayHeight != null && displayHeight > 0) body.put("display_height", displayHeight);
            if (agentHost != null && !agentHost.isBlank()) body.put("agent_host", agentHost);
            if (wdaPort != null && wdaPort > 0) body.put("wda_port", wdaPort);
            Map<String, Object> resp = postJsonForMap("/api/v1/device/inspect-point", body);
            if (resp.isEmpty()) {
                return Map.of("source", "coordinate", "context", "native", "valid", false,
                        "inspect_error", "executor_unavailable");
            }
            return resp;
        } catch (AppException e) {
            log.warn("inspectPoint failed serial={} ({},{}): {}", serialNumber, x, y, e.getMessage());
            return Map.of("source", "coordinate", "context", "native", "valid", false,
                    "inspect_error", "executor_unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> switchContext(String serialNumber, String target) {
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("serial_number", serialNumber);
            body.put("target", target != null ? target : "auto");
            Map<String, Object> resp = postJsonForMap("/api/v1/device/switch-context", body);
            return resp.isEmpty() ? Map.of("ok", false) : resp;
        } catch (Exception e) {
            log.warn("switchContext failed serial={}: {}", serialNumber, e.getMessage());
            return Map.of("ok", false);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> deployWda(String serialNumber, Map<String, Object> config) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("serial_number", serialNumber);
        if (config != null) {
            body.putAll(config);
        }
        Map<String, Object> resp = postJsonForMap("/api/v1/device/wda-deploy", body);
        if (resp.isEmpty()) {
            return Map.of("success", false, "message", "执行器无响应");
        }
        return resp;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> wdaStatus(String agentHost, int wdaPort) {
        Map<String, Object> resp = postJsonForMap("/api/v1/device/wda-status", Map.of(
                "agent_host", agentHost != null ? agentHost : "127.0.0.1",
                "wda_port", wdaPort
        ));
        if (resp.isEmpty()) {
            return Map.of("ok", false);
        }
        return resp;
    }

    private ExecutorRequest buildRequest(TestTask task, TaskExecution execution, Device device) {
        ExecutorRequest req = new ExecutorRequest();
        req.setTaskId(task.getId());
        req.setExecutionId(execution.getId());
        req.setDeviceId(device.getId());
        req.setSerialNumber(device.getSerialNumber());
        req.setPlatform(device.getPlatform().name());
        String scriptType = task.getScriptType();
        String scriptContent = task.getScriptContent();
        Map<String, String> vars = variableContextService.resolveForTask(task);
        if ("visual".equals(scriptType)) {
            int startStep = task.getResumeFromStep() != null ? task.getResumeFromStep() : 1;
            scriptContent = visualScriptGenerator.generate(
                    task.getScriptContent(), startStep, vars, task.getAppPackage());
            scriptType = "python";
        }
        req.setScriptType(scriptType);
        req.setScriptContent(scriptContent);
        try {
            req.setVariablesJson(objectMapper.writeValueAsString(vars));
        } catch (Exception e) {
            req.setVariablesJson("{}");
        }
        req.setAppPackage(task.getAppPackage());
        if (task.getAppPackageId() != null) {
            req.setAppPath(appPackageRepository.findById(task.getAppPackageId())
                    .map(p -> p.getFilePath())
                    .orElse(null));
        } else {
            req.setAppPath(task.getAppPath());
        }
        req.setEnableRecording(task.getEnableRecording());
        req.setAgentHost(device.getAgentHost());
        req.setAgentPort(device.getAgentPort());
        req.setWdaPort(device.getWdaPort());
        req.setAdbPort(device.getAdbPort());
        req.setTimeoutSeconds(task.getTimeoutSeconds());
        req.setResolvedControls(controlResolverService.buildControlsJson(task));
        if (device.getCalibrationJson() != null && !device.getCalibrationJson().isBlank()) {
            req.setCalibrationJson(device.getCalibrationJson());
        } else {
            req.setCalibrationJson("{\"offset_x\":0,\"offset_y\":0,\"scale_x\":1,\"scale_y\":1}");
        }
        req.setAutoGrantPermissions(true);
        req.setAutoRevokePermissions(true);
        req.setAutoCollectControls(true);
        req.setExecutionIsolation(true);
        req.setAssertPolicyJson(assertPolicyService.buildPolicyJson());
        if (task.getCreatedBy() != null) {
            req.setOperatorId(task.getCreatedBy());
            userRepository.findById(task.getCreatedBy()).ifPresent(u -> {
                String label = u.getDisplayName() != null && !u.getDisplayName().isBlank()
                        ? u.getDisplayName() : u.getUsername();
                req.setOperatorLabel(label);
            });
        }
        return req;
    }

    @SuppressWarnings("unchecked")
    private List<LogEntry> parseLogs(Object logsObj) {
        List<LogEntry> result = new ArrayList<>();
        if (logsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    result.add(new LogEntry(
                            str(m.get("log_type"), "script"),
                            str(m.get("level"), "info"),
                            str(m.get("message"), "")
                    ));
                }
            }
        } else if (logsObj instanceof Map<?, ?> m && m.containsKey("stdout")) {
            result.add(new LogEntry("script", "info", str(m.get("stdout"), "")));
        }
        return result;
    }

    private String str(Object o, String def) {
        return o != null ? o.toString() : def;
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ExecutorRequest {
        private Long taskId;
        private Long executionId;
        private Long deviceId;
        private String serialNumber;
        private String platform;
        private String scriptType;
        private String scriptContent;
        private String appPackage;
        private String appPath;
        private Boolean enableRecording;
        private String agentHost;
        private Integer agentPort;
        private Integer wdaPort;
        private Integer adbPort;
        private Integer timeoutSeconds;
        private String resolvedControls;
        private String variablesJson;
        private String calibrationJson;
        private Boolean autoGrantPermissions;
        private Boolean autoRevokePermissions;
        private Boolean autoCollectControls;
        private Boolean executionIsolation;
        private Long operatorId;
        private String operatorLabel;
        private String assertPolicyJson;
    }

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ExecutorResponse {
        private boolean success;
        private String summary;
        private String errorMessage;
        private String recordingPath;
        private String watermarkHash;
        private Long fileSize;
        private Object logs;
        private String operatorLabel;
    }

    public record LogEntry(String logType, String level, String message) {}

    public record ExecutorResult(boolean success, String summary, String recordingPath,
                                 String watermarkHash, long fileSize, List<LogEntry> logs,
                                 String errorMessage, String operatorLabel) {
        public static ExecutorResult success(String summary, String recordingPath, String watermarkHash,
                                             long fileSize, List<LogEntry> logs, String operatorLabel) {
            return new ExecutorResult(true, summary, recordingPath, watermarkHash, fileSize, logs, null, operatorLabel);
        }
        public static ExecutorResult failed(String error) {
            return new ExecutorResult(false, null, null, null, 0, List.of(), error, null);
        }
        public static ExecutorResult failed(String error, List<LogEntry> logs) {
            return new ExecutorResult(false, null, null, null, 0, logs, error, null);
        }
    }
}
