package com.atp.platform.service.aicase;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成标准功能测试用例 JSON（非自动化脚本）：优先 LLM，无 Key 时 offline。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCaseGenerator {

    private static final Pattern JSON_BLOCK = Pattern.compile("(?s)\\{.*\\}");

    private final AtpProperties properties;
    private final ObjectMapper mapper;
    private final RestClient.Builder restClientBuilder;
    private final TestBrainClient testBrainClient;

    public JsonNode generate(String platform, String appPackage, String prdText) {
        AtpProperties.AiCase cfg = properties.getAiCase();
        String provider = cfg.getProvider() == null ? "llm" : cfg.getProvider().trim().toLowerCase();
        boolean hasKey = cfg.getLlmApiKey() != null && !cfg.getLlmApiKey().isBlank();

        if ("offline".equals(provider)) {
            return offlineDraft(platform, appPackage, prdText, cfg.getMaxCases());
        }
        if ("testbrain".equals(provider)) {
            try {
                return generateViaTestBrain(platform, appPackage, prdText);
            } catch (Exception e) {
                log.warn("TestBrain 调用失败，降级: {}", e.getMessage());
                if (hasKey) return generateViaLlm(platform, appPackage, prdText);
                return offlineDraft(platform, appPackage, prdText, cfg.getMaxCases());
            }
        }
        if (!hasKey) {
            return offlineDraft(platform, appPackage, prdText, cfg.getMaxCases());
        }
        return generateViaLlm(platform, appPackage, prdText);
    }

    private JsonNode generateViaLlm(String platform, String appPackage, String prdText) {
        AtpProperties.AiCase cfg = properties.getAiCase();
        if (cfg.getLlmApiKey() == null || cfg.getLlmApiKey().isBlank()) {
            return offlineDraft(platform, appPackage, prdText, cfg.getMaxCases());
        }
        String base = trimSlash(cfg.getLlmBaseUrl());
        String chatPath = base.endsWith("/v1") ? "/chat/completions" : "/v1/chat/completions";
        Map<String, Object> body = Map.of(
                "model", cfg.getLlmModel(),
                "temperature", 0.1,
                "messages", List.of(
                        Map.of("role", "system", "content", AiCasePromptBuilder.buildSystemPrompt()),
                        Map.of("role", "user", "content",
                                AiCasePromptBuilder.buildUserPrompt(platform, appPackage, prdText, cfg.getMaxCases()))
                )
        );
        try {
            RestClient client = restClientBuilder.baseUrl(base).build();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.post()
                    .uri(chatPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + cfg.getLlmApiKey())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String content = extractChatContent(resp);
            JsonNode parsed = parseJsonContent(content);
            if (parsed.path("cases").isArray() && !parsed.path("cases").isEmpty()) {
                return parsed;
            }
            log.warn("LLM 返回无 cases，降级 offline");
            JsonNode offline = offlineDraft(platform, appPackage, prdText, cfg.getMaxCases());
            ((ObjectNode) offline).put("note", "LLM 未返回有效用例，已降级为 offline");
            return offline;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LLM 生成失败，降级 offline: {}", e.getMessage());
            JsonNode offline = offlineDraft(platform, appPackage, prdText, cfg.getMaxCases());
            ((ObjectNode) offline).put("note", "LLM 调用失败已降级 offline: " + e.getMessage());
            return offline;
        }
    }

    private JsonNode generateViaTestBrain(String platform, String appPackage, String prdText) {
        AtpProperties.AiCase cfg = properties.getAiCase();
        JsonNode raw = testBrainClient.generateCases(platform, appPackage, prdText, cfg.getMaxCases());
        if (raw.path("cases").isArray() && !raw.path("cases").isEmpty()) {
            if (raw instanceof ObjectNode on && !on.has("provider")) {
                on.put("provider", "testbrain");
            }
            return raw;
        }
        throw new IllegalStateException("TestBrain 未返回有效 cases: " + raw.path("message").asText(raw.toString()));
    }

    public JsonNode offlineDraft(String platform, String appPackage, String prdText, int maxCases) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode cases = root.putArray("cases");
        String prd = prdText == null ? "" : prdText.trim();
        List<String> flowLines = extractFlowLines(prd);
        List<String> features = extractFeatures(prd);
        boolean robotBiz = mentionsRobot(prd);
        boolean appBiz = mentionsApp(prd);
        boolean loginPrimary = isLoginPrimaryTopic(prd);
        String module = guessModule(prd, robotBiz, loginPrimary);

        cases.add(buildEndToEndCase(module, prd, flowLines, features, appBiz, robotBiz));

        if (loginPrimary && !robotBiz) {
            cases.add(standardCase(
                    "tc-p1：登录页输入正确账号密码点击登录进入首页",
                    "登录/账号",
                    1,
                    "功能",
                    "1. App 已安装并可启动；2. 网络正常；3. 已准备有效测试账号",
                    "账号：有效用户；密码：正确密码",
                    "登录成功并进入首页/主界面，无错误提示",
                    List.of(
                            Map.of("step", "启动应用并进入登录页", "expected", "展示账号、密码输入框及登录按钮"),
                            Map.of("step", "输入正确的账号和密码", "expected", "输入内容正确回显（密码可掩码）"),
                            Map.of("step", "点击「登录」按钮", "expected", "发起登录请求，出现加载或跳转"),
                            Map.of("step", "观察登录结果页", "expected", "进入首页/主界面，不出现错误提示")
                    )
            ));
        }

        int limit = Math.max(2, Math.min(maxCases, 6));
        for (String f : features) {
            if (cases.size() >= limit) break;
            if (isTooGeneric(f)) continue;
            cases.add(standardCase(
                    "tc-p2：业务页按需求完成「" + truncate(f, 18) + "」得到文档所述结果",
                    module,
                    2,
                    "功能",
                    "1. 测试环境可用；2. 已具备需求所述前置（账号/机器人/数据）",
                    "按需求文档中的业务数据",
                    "结果与需求描述一致：" + truncate(f, 40),
                    List.of(
                            Map.of("step", "在 App 中进入与「" + truncate(f, 20) + "」对应的功能入口（以需求文档为准）",
                                    "expected", "进入正确业务页面，关键信息可见"),
                            Map.of("step", "按需求执行：「" + truncate(f, 28) + "」",
                                    "expected", "操作被系统接受，过程符合需求规则"),
                            Map.of("step", "核对 App 展示/状态" + (robotBiz ? "及机器人侧反馈" : ""),
                                    "expected", "展示与需求验收点一致，无矛盾状态")
                    )
            ));
        }
        if (cases.size() == 1) {
            String f = features.isEmpty() ? "主功能校验" : features.get(0);
            cases.add(standardCase(
                    "tc-p2：业务页对「" + truncate(f, 16) + "」使用异常条件被拦截或提示",
                    module,
                    2,
                    "异常",
                    "1. 可构造需求允许的异常条件；2. 环境可用",
                    "异常/缺失/离线等（以需求为准）",
                    "系统按需求拒绝或提示，不产生错误业务结果",
                    List.of(
                            Map.of("step", "进入相关业务页", "expected", "页面可打开"),
                            Map.of("step", "按异常条件操作「" + truncate(f, 20) + "」", "expected", "操作失败或被拦截"),
                            Map.of("step", "查看提示与对象状态", "expected", "提示明确；App/机器人状态不被错误推进")
                    )
            ));
        }

        root.put("provider", "offline");
        root.put("note", "offline：已强制拼端到端主路径；建议用 LLM 以更贴合原文");
        return root;
    }

    private ObjectNode buildEndToEndCase(String module, String prd, List<String> flowLines,
                                         List<String> features, boolean appBiz, boolean robotBiz) {
        List<Map<String, String>> steps = new ArrayList<>();
        steps.add(Map.of(
                "step", appBiz ? "打开/启动业务 App，进入需求所述首页或工作台" : "进入需求所述业务入口",
                "expected", "App/入口可用，关键导航或首页元素可见"
        ));

        List<String> chain = !flowLines.isEmpty() ? flowLines : features;
        int used = 0;
        for (String line : chain) {
            if (used >= 8) break;
            if (isTooGeneric(line)) continue;
            steps.add(Map.of(
                    "step", "按需求执行：" + truncate(line, 60),
                    "expected", "该步骤完成后，界面/状态符合需求对该点的描述"
            ));
            used++;
        }

        if (robotBiz) {
            boolean hasRobotStep = steps.stream().anyMatch(s ->
                    s.get("step").contains("机器人") || s.get("expected").contains("机器人"));
            if (!hasRobotStep) {
                steps.add(Map.of(
                        "step", "在 App 中选择/关联目标机器人（或设备），确认其在线或可用（以需求为准）",
                        "expected", "目标机器人已选中且状态满足下发条件"
                ));
                steps.add(Map.of(
                        "step", "在 App 发起需求所述任务/指令并下发到机器人",
                        "expected", "下发成功；App 显示已下发/执行中等状态"
                ));
                steps.add(Map.of(
                        "step", "观察机器人侧开始执行，并在 App 查看回传/进度/结果",
                        "expected", "机器人按任务执行；App 能看到与需求一致的状态或结果回传"
                ));
            } else {
                steps.add(Map.of(
                        "step", "确认机器人侧已进入执行，并回到 App 核对最终状态/结果展示",
                        "expected", "App 与机器人状态一致，满足需求主流程验收"
                ));
            }
        } else if (used == 0) {
            steps.add(Map.of("step", "按需求完成主业务操作", "expected", "得到需求描述的业务结果"));
            steps.add(Map.of("step", "核对关键页面展示与数据", "expected", "与需求验收一致"));
        }

        String titleCore = robotBiz
                ? "App按需求完成配置并下发至机器人机器人执行且App可见结果"
                : "App按需求完成主业务流程得到文档所述结果";
        String expected = robotBiz
                ? "从 App 入口到机器人执行的主路径跑通，App 可见符合需求的结果/状态回传"
                : "主业务流程按需求完成，关键结果可核对";

        return standardCase(
                "tc-p0：" + titleCore,
                module,
                0,
                "冒烟",
                buildE2ePreconditions(prd, robotBiz),
                "使用需求文档中的业务账号、机器人/设备及任务数据",
                expected,
                steps
        );
    }

    private static String buildE2ePreconditions(String prd, boolean robotBiz) {
        StringBuilder sb = new StringBuilder();
        sb.append("1. 测试环境与网络可用；2. 已具备需求所述账号/权限");
        if (robotBiz) sb.append("；3. 目标机器人（或设备）在线且满足任务前置");
        if (prd != null && prd.contains("地图")) sb.append("；4. 相关地图/场景数据已就绪（若需求要求）");
        return sb.toString();
    }

    private ObjectNode standardCase(String name, String module, int priority, String caseType,
                                    String preconditions, String testData, String expected,
                                    List<Map<String, String>> steps) {
        ObjectNode c = mapper.createObjectNode();
        c.put("name", name);
        c.put("module_name", module);
        c.put("priority", priority);
        c.put("case_type", caseType);
        c.put("preconditions", preconditions);
        c.put("test_data", testData);
        c.put("expected_result", expected);
        c.put("tags", "ai,offline,standard");
        ArrayNode arr = c.putArray("steps");
        for (Map<String, String> s : steps) {
            ObjectNode n = arr.addObject();
            n.put("step", s.get("step"));
            n.put("expected", s.get("expected"));
        }
        return c;
    }

    private static boolean mentionsRobot(String prd) {
        if (prd == null) return false;
        return prd.contains("机器人") || prd.toLowerCase().contains("robot");
    }

    private static boolean mentionsApp(String prd) {
        if (prd == null) return false;
        String t = prd.toLowerCase();
        return prd.contains("App") || prd.contains("APP") || prd.contains("应用")
                || t.contains("android") || t.contains("ios") || prd.contains("客户端");
    }

    private static boolean isLoginPrimaryTopic(String prd) {
        if (prd == null || prd.isBlank()) return false;
        if (mentionsRobot(prd)) return false;
        int score = 0;
        if (prd.contains("登录") || prd.contains("登陆")) score += 3;
        if (prd.toLowerCase().contains("login")) score += 2;
        if (prd.contains("密码")) score += 1;
        if (prd.contains("注册")) score += 1;
        if (prd.contains("任务") || prd.contains("巡检") || prd.contains("地图") || prd.contains("下发")) score -= 2;
        return score >= 3;
    }

    private static String guessModule(String prd, boolean robotBiz, boolean loginPrimary) {
        if (loginPrimary) return "登录/账号";
        if (robotBiz) return "App与机器人业务";
        if (prd == null || prd.isBlank()) return "未分类";
        List<String> features = extractFeatures(prd);
        return features.isEmpty() ? "功能验证" : truncate(features.get(0), 16);
    }

    private static boolean isTooGeneric(String t) {
        if (t == null) return true;
        String s = t.replaceAll("\\s+", "");
        return s.length() < 6
                || s.equals("功能说明")
                || s.equals("验收标准")
                || s.startsWith("如下")
                || s.startsWith("见图");
    }

    private static List<String> extractFlowLines(String prd) {
        List<String> out = new ArrayList<>();
        if (prd == null || prd.isBlank()) return out;
        for (String line : prd.split("\\R")) {
            String t = line.trim()
                    .replaceFirst("^[0-9]+[.、\\)]\\s*", "")
                    .replaceFirst("^[-*•]+\\s*", "")
                    .replaceFirst("^[（(]?[0-9]+[)）]\\s*", "")
                    .trim();
            if (t.isEmpty() || isSectionHeader(t) || isTooGeneric(t)) continue;
            if (t.length() < 8 || t.length() > 120) continue;
            boolean actionable = containsAny(t,
                    "打开", "进入", "点击", "选择", "创建", "下发", "发送", "启动", "开始",
                    "停止", "查看", "确认", "配置", "绑定", "连接", "执行", "回传", "上报",
                    "机器人", "任务", "巡检", "地图", "登录");
            if (!actionable) continue;
            out.add(t);
            if (out.size() >= 12) break;
        }
        return out;
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private static List<String> extractFeatures(String prd) {
        List<String> out = new ArrayList<>();
        if (prd == null || prd.isBlank()) return out;
        for (String line : prd.split("\\R")) {
            String t = line.trim()
                    .replaceFirst("^[0-9]+[.、\\)]\\s*", "")
                    .replaceFirst("^[-*•]+\\s*", "")
                    .trim();
            if (t.isEmpty()) continue;
            if (isSectionHeader(t)) continue;
            if (t.length() < 6 || t.length() > 100) continue;
            if (isTooGeneric(t)) continue;
            out.add(t);
            if (out.size() >= 10) break;
        }
        return out;
    }

    private static boolean isSectionHeader(String t) {
        String s = t.replaceAll("\\s+", "");
        if (s.matches("^[一二三四五六七八九十]+[、.．].*")) {
            if (s.contains("背景") || s.contains("目录") || s.contains("修订") || s.contains("术语")
                    || s.contains("概述") || s.contains("说明") || s.contains("范围") || s.contains("附录")) {
                return true;
            }
            if (s.length() <= 12) return true;
        }
        return s.startsWith("需求背景") || s.startsWith("修订记录") || s.startsWith("文档说明");
    }

    private JsonNode parseJsonContent(String content) {
        if (content == null || content.isBlank()) {
            throw new AppException("AI_CASE", "模型返回空内容", HttpStatus.BAD_GATEWAY);
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)```\\s*$", "").trim();
        }
        try {
            return mapper.readTree(text);
        } catch (Exception ignored) {
            Matcher m = JSON_BLOCK.matcher(text);
            if (m.find()) {
                try {
                    return mapper.readTree(m.group());
                } catch (Exception e2) {
                    throw new AppException("AI_CASE", "无法解析模型 JSON: " + e2.getMessage(), HttpStatus.BAD_GATEWAY);
                }
            }
            throw new AppException("AI_CASE", "模型未返回合法 JSON", HttpStatus.BAD_GATEWAY);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractChatContent(Map<String, Object> resp) {
        if (resp == null) return "";
        Object choices = resp.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) return "";
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice)) return "";
        Object msg = choice.get("message");
        if (!(msg instanceof Map<?, ?> message)) return "";
        Object content = message.get("content");
        return content == null ? "" : String.valueOf(content);
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
