package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.*;
import com.atp.platform.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VariableContextService {

    private final AtpProperties properties;
    private final ObjectMapper objectMapper;
    private final TestEnvironmentRepository environmentRepository;
    private final TestCaseRepository caseRepository;
    private final TestSuiteRepository suiteRepository;
    private final SuiteRunRepository suiteRunRepository;
    private final DataSetRowRepository dataSetRowRepository;
    private final SecureCredentialRepository credentialRepository;
    private final CryptoService cryptoService;
    private final GlobalParameterService globalParameterService;
    private final TestAccountService testAccountService;
    private final WaitTemplateService waitTemplateService;

    public Map<String, String> resolveForTask(TestTask task) {
        Map<String, String> vars = new LinkedHashMap<>();
        if (properties.getVariables() != null) {
            vars.putAll(properties.getVariables());
        }
        vars.putAll(globalParameterService.resolvePlatformParams());
        mergeEnvVars(vars, task.getEnvId());
        vars.putAll(globalParameterService.resolveEnvParams(task.getEnvId()));
        if (task.getSuiteRunId() != null) {
            suiteRunRepository.findById(task.getSuiteRunId()).ifPresent(run -> {
                if (run.getSuiteId() != null) {
                    suiteRepository.findById(run.getSuiteId()).ifPresent(suite -> mergeEnvVars(vars, suite.getEnvId()));
                }
            });
        }
        if (task.getSourceCaseId() != null) {
            caseRepository.findById(task.getSourceCaseId()).ifPresent(c -> {
                mergeEnvVars(vars, c.getEnvId());
                mergeJsonVars(vars, c.getStepsContent());
            });
        }
        if (task.getDatasetRowId() != null) {
            dataSetRowRepository.findById(task.getDatasetRowId()).ifPresent(row -> mergeJsonVars(vars, row.getRowDataJson()));
        }
        mergeJsonVars(vars, task.getVariablesJson());
        applyWaitTemplate(vars);
        mergeCredentialVars(vars, task.getEnvId());
        mergeAccountVars(vars, task.getTestAccountId());
        return vars;
    }

    private void mergeAccountVars(Map<String, String> vars, Long accountId) {
        if (accountId == null) return;
        vars.putAll(testAccountService.credentialsForTask(accountId));
    }

    private void applyWaitTemplate(Map<String, String> vars) {
        String tpl = vars.get("WAIT_TEMPLATE");
        if (tpl == null || tpl.isBlank()) return;
        vars.putAll(waitTemplateService.resolve(tpl));
    }

    private void mergeCredentialVars(Map<String, String> vars, Long envId) {
        credentialRepository.findAllByOrderByUpdatedAtDesc().forEach(c -> {
            if (envId != null && c.getEnvId() != null && !envId.equals(c.getEnvId())) return;
            vars.put("SECRET_" + c.getName(), cryptoService.decrypt(c.getValueCipher()));
        });
    }

    private void mergeEnvVars(Map<String, String> vars, Long envId) {
        if (envId == null) return;
        environmentRepository.findById(envId).ifPresent(env -> {
            if (env.getBaseUrl() != null && !env.getBaseUrl().isBlank()) {
                vars.put("BASE_URL", env.getBaseUrl());
            }
            mergeJsonVars(vars, env.getConfigJson());
        });
    }

    private void mergeJsonVars(Map<String, String> vars, String json) {
        if (json == null || json.isBlank()) return;
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isObject()) {
                if (node.has("variables") && node.get("variables").isObject()) {
                    node.get("variables").fields().forEachRemaining(e ->
                            vars.put(e.getKey(), e.getValue().asText("")));
                } else {
                    node.fields().forEachRemaining(e -> {
                        if (e.getValue().isValueNode()) {
                            vars.put(e.getKey(), e.getValue().asText(""));
                        }
                    });
                }
                if (node.has("wait_template") && !node.path("wait_template").asText("").isBlank()) {
                    vars.put("WAIT_TEMPLATE", node.path("wait_template").asText());
                }
            }
        } catch (Exception ignored) {
        }
    }
}
