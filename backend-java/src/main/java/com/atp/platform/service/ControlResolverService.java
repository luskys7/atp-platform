package com.atp.platform.service;

import com.atp.platform.dto.ControlPoolLookupContext;
import com.atp.platform.entity.TestTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ControlResolverService {

    private final ControlPoolService controlPoolService;
    private final ObjectMapper objectMapper;

    /** 为执行任务构建控件映射 JSON，供 Python 执行器注入环境变量 */
    public String buildControlsJson(TestTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Map<String, Object>> elements = new LinkedHashMap<>();

        List<ControlPoolService.ResolvedControl> resolved = controlPoolService.resolveAllForTask(task);
        for (ControlPoolService.ResolvedControl rc : resolved) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("locator_type", rc.locatorType());
            item.put("locator_value", rc.locatorValue());
            item.put("source", rc.source());
            if (rc.stepIndex() != null) {
                item.put("step_index", rc.stepIndex());
            }
            if (rc.locators() != null && !rc.locators().isEmpty()) {
                item.put("locators", rc.locators());
            }
            if (rc.locatorChain() != null && !rc.locatorChain().isEmpty()) {
                item.put("locator_chain", rc.locatorChain());
            }
            if (rc.waitRule() != null && !rc.waitRule().isEmpty()) {
                item.put("wait_rule", rc.waitRule());
            }
            elements.put(rc.key(), item);
        }
        payload.put("elements", elements);
        payload.put("app_package", task.getAppPackage());
        payload.put("task_id", task.getId());
        ControlPoolLookupContext ctx = controlPoolService.contextFromTask(task);
        payload.put("version_tag", ctx.versionTag());
        payload.put("env_tag", ctx.envTag());
        payload.put("team_id", ctx.teamId());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"elements\":{}}";
        }
    }

    /** 解析单个控件（供 API 调试） */
    public ControlPoolService.ResolvedControl resolve(Long taskId, Integer stepIndex,
                                                      String elementName, String appPackage) {
        return controlPoolService.resolveLocator(taskId, stepIndex, elementName, appPackage);
    }
}
