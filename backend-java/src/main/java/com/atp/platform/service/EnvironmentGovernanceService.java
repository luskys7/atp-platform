package com.atp.platform.service;

import com.atp.platform.entity.AppPackage;
import com.atp.platform.entity.TestEnvironment;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.AppPackageRepository;
import com.atp.platform.repository.TestEnvironmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnvironmentGovernanceService {

    private final TestEnvironmentRepository environmentRepository;
    private final AppPackageRepository appPackageRepository;
    private final ObjectMapper objectMapper;

    public void assertTaskAllowed(TestTask task) {
        if (task.getEnvId() == null) return;
        TestEnvironment env = environmentRepository.findById(task.getEnvId())
                .orElseThrow(() -> new AppException("ENV_NOT_FOUND", "执行环境不存在", HttpStatus.BAD_REQUEST));

        JsonNode cfg = parseConfig(env.getConfigJson());

        if (env.getEnvType() == TestEnvironment.EnvType.prod) {
            boolean allow = cfg.path("allow_automation").asBoolean(false);
            if (!allow) {
                throw new AppException("ENV_BLOCKED", "生产环境默认禁止自动化执行，请在环境 config_json 设置 allow_automation=true", HttpStatus.FORBIDDEN);
            }
        }

        if (env.getEnvType() == TestEnvironment.EnvType.gray) {
            String whitelist = cfg.path("gray_device_tags").asText(cfg.path("device_tags").asText(""));
            if (!whitelist.isBlank()) {
                Set<String> allowed = splitTags(whitelist);
                Set<String> taskTags = splitTags(task.getDeviceTags());
                if (taskTags.isEmpty() || taskTags.stream().noneMatch(allowed::contains)) {
                    throw new AppException("GRAY_ISOLATION",
                            "灰度环境要求任务 device_tags 命中白名单: " + whitelist, HttpStatus.FORBIDDEN);
                }
            }
        }

        if (task.getAppPackageId() != null) {
            AppPackage pkg = appPackageRepository.findById(task.getAppPackageId()).orElse(null);
            if (pkg != null && pkg.getPackageChannel() == AppPackage.PackageChannel.production
                    && env.getEnvType() != TestEnvironment.EnvType.prod) {
                throw new AppException("CHANNEL_ENV_MISMATCH", "生产渠道包不可在非生产环境执行", HttpStatus.FORBIDDEN);
            }
            if (pkg != null && pkg.getPackageChannel() == AppPackage.PackageChannel.beta
                    && env.getEnvType() == TestEnvironment.EnvType.test) {
                throw new AppException("CHANNEL_ENV_MISMATCH", "灰度渠道包请在 gray/staging 环境执行", HttpStatus.FORBIDDEN);
            }
        }
    }

    private JsonNode parseConfig(String json) {
        try {
            if (json == null || json.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private Set<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
