package com.atp.platform.service.aicase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 AI 标准测试用例转为平台可落库的 draft（steps_content 保留步骤说明，非自动化定位脚本）。
 */
@Component
public class AiCaseAdapter {

    private final ObjectMapper mapper;

    public AiCaseAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> toCaseDrafts(JsonNode root, String platform, String appPackage) {
        List<Map<String, Object>> out = new ArrayList<>();
        JsonNode cases = root.path("cases");
        if (!cases.isArray() || cases.isEmpty()) {
            if (root.has("steps") || root.has("name")) {
                out.add(toOneCase(root, platform, appPackage));
            }
            return out;
        }
        for (JsonNode c : cases) {
            out.add(toOneCase(c, platform, appPackage));
        }
        return out;
    }

    public Map<String, Object> toOneCase(JsonNode c, String platform, String appPackage) {
        String plat = platform == null || platform.isBlank() ? "android" : platform.trim();
        String pkg = appPackage == null ? "" : appPackage.trim();

        List<Map<String, String>> standardSteps = extractStandardSteps(c.path("steps"));
        if (standardSteps.isEmpty()) {
            standardSteps = List.of(Map.of(
                    "step", "根据需求完成主流程操作（请人工细化）",
                    "expected", text(c, "expected_result", "功能符合需求描述")
            ));
        }

        String preconditions = text(c, "preconditions", "1. 测试环境可用；2. 测试账号已准备");
        String expectedResult = text(c, "expected_result", "");
        if (expectedResult.isBlank()) {
            expectedResult = standardSteps.get(standardSteps.size() - 1).get("expected");
        }
        String testData = text(c, "test_data", "无");
        String caseType = text(c, "case_type", "功能");
        int priority = c.path("priority").isNumber() ? c.path("priority").asInt(2) : 2;
        // 冒烟统一为 P0
        if ("冒烟".equals(caseType) || priority == 0
                || text(c, "name", "").toLowerCase(Locale.ROOT).contains("tc-p0")) {
            priority = 0;
            if (!"冒烟".equals(caseType)) caseType = "冒烟";
        } else if (priority < 1 || priority > 3) {
            priority = 2;
        }

        ObjectNode visual = mapper.createObjectNode();
        visual.put("version", 1);
        visual.put("case_format", "standard");
        visual.put("human_delay", false);
        visual.put("on_fail", "fail");
        visual.put("screenshot_policy", "on_fail");
        visual.put("wait_template", "standard");
        ArrayNode stdArr = visual.putArray("standard_steps");
        ArrayNode steps = visual.putArray("steps");
        int i = 1;
        for (Map<String, String> st : standardSteps) {
            ObjectNode row = stdArr.addObject();
            row.put("step_no", i);
            row.put("step", st.get("step"));
            row.put("expected", st.get("expected"));

            ObjectNode step = steps.addObject();
            step.put("type", "wait");
            step.put("catalog_id", "flow.wait");
            step.put("seconds", 1);
            step.put("wait_mode", "fixed");
            step.put("enabled", false);
            step.put("on_fail", "fail");
            step.put("remark", "步骤" + i + "：" + st.get("step"));
            step.put("expected", st.get("expected"));
            step.put("disable_reason", "标准测试用例步骤（非自动化执行）");
            i++;
        }

        String name = normalizeCaseTitle(text(c, "name", "未命名用例"), priority, expectedResult, standardSteps);
        String tags = text(c, "tags", "ai");
        if (!tags.contains("ai")) {
            tags = tags.isBlank() ? "ai" : tags + ",ai";
        }
        if (!tags.contains("standard")) {
            tags = tags + ",standard";
        }

        LinkedHashMap<String, Object> draft = new LinkedHashMap<>();
        draft.put("name", name);
        draft.put("platform", plat);
        draft.put("script_type", "visual");
        draft.put("steps_content", visual.toString());
        draft.put("app_package", pkg);
        draft.put("module_name", text(c, "module_name", "未分类"));
        draft.put("priority", priority);
        draft.put("preconditions", preconditions);
        draft.put("expected_result", expectedResult);
        draft.put("tags", tags);
        draft.put("case_status", "draft");
        draft.put("enable_recording", false);
        draft.put("timeout_seconds", 3600);
        draft.put("case_type", caseType);
        draft.put("test_data", testData);
        draft.put("standard_steps", standardSteps);
        draft.put("step_count", standardSteps.size());
        draft.put("pending_steps", 0);
        draft.put("assert_count", (int) standardSteps.stream().filter(s -> !s.get("expected").isBlank()).count());
        draft.put("ready_for_editor", !standardSteps.isEmpty() && !expectedResult.isBlank());
        return draft;
    }

    private List<Map<String, String>> extractStandardSteps(JsonNode stepArr) {
        List<Map<String, String>> out = new ArrayList<>();
        if (!stepArr.isArray()) return out;
        for (JsonNode s : stepArr) {
            // 新格式：step + expected
            String step = text(s, "step", text(s, "description", text(s, "remark", "")));
            String expected = text(s, "expected", "");
            // 兼容旧自动化格式
            if (step.isBlank()) {
                String action = text(s, "action", text(s, "type", ""));
                String element = text(s, "element_name", "");
                if (!action.isBlank()) {
                    step = describeLegacyAction(action, element, text(s, "text", ""));
                }
            }
            if (step.isBlank()) continue;
            if (expected.isBlank()) expected = "操作成功，界面与需求一致";
            out.add(Map.of("step", step, "expected", expected));
        }
        return out;
    }

    private static String describeLegacyAction(String action, String element, String text) {
        String a = action.toLowerCase(Locale.ROOT);
        return switch (a) {
            case "launch", "start_app" -> "启动被测应用";
            case "click", "tap" -> "点击「" + (element.isBlank() ? "目标控件" : element) + "」";
            case "input", "type_text" -> "在「" + (element.isBlank() ? "输入框" : element) + "」输入"
                    + (text.isBlank() ? "测试数据" : text);
            case "wait", "sleep" -> "等待页面加载完成";
            case "assert_text", "assert_exists", "exists" -> "检查「"
                    + (element.isBlank() ? "目标界面/控件" : element) + "」是否符合预期";
            case "screenshot" -> "截图留存";
            default -> action + (element.isBlank() ? "" : "：" + element);
        };
    }

    /**
     * 标题规范：tc-px：在什么地方做什么事情得到什么结果
     * x 仅取 0~3（0=冒烟）；会剥掉模型自带的任意 tc-pN 前缀，避免重复。
     */
    static String normalizeCaseTitle(String raw, int priority, String expectedResult, List<Map<String, String>> steps) {
        int p = (priority < 0 || priority > 3) ? 2 : priority;
        String prefix = "tc-p" + p + "：";
        String body = raw == null ? "" : raw.trim();
        // 循环去掉已有 tc-pN: / tc-pN： 前缀（含 p4+、以及已重复的前缀）
        for (int i = 0; i < 5; i++) {
            String next = body.replaceFirst("(?i)^tc-p\\d+\\s*[:：]\\s*", "").trim();
            if (next.equals(body)) break;
            body = next;
        }
        body = body.replaceFirst("^AI草案[-—:：]*", "").trim();
        body = body.replaceFirst("^(主流程|异常|验证|冒烟)[-—:：]*", "").trim();
        if (body.isBlank()) {
            body = buildTitleBodyFromSteps(steps, expectedResult);
        }
        body = body.replaceAll("\\s+", "");
        if (body.isBlank()) body = "目标页完成操作得到预期结果";
        return prefix + body;
    }

    private static String buildTitleBodyFromSteps(List<Map<String, String>> steps, String expectedResult) {
        if (steps == null || steps.isEmpty()) {
            return expectedResult == null || expectedResult.isBlank() ? "目标页完成操作得到预期结果" : expectedResult;
        }
        String first = steps.get(0).getOrDefault("step", "");
        String lastExpected = steps.get(steps.size() - 1).getOrDefault("expected", "");
        if (lastExpected.isBlank()) lastExpected = expectedResult == null ? "" : expectedResult;
        String combined = first + (lastExpected.isBlank() ? "" : lastExpected);
        if (combined.length() > 48) combined = combined.substring(0, 48);
        return combined.isBlank() ? "目标页完成操作得到预期结果" : combined;
    }

    private static String text(JsonNode n, String field, String def) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) return def;
        String s = v.asText("").trim();
        return s.isEmpty() ? def : s;
    }
}
