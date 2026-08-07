package com.atp.platform.service.aicase;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.TestCase;
import com.atp.platform.exception.AppException;
import com.atp.platform.service.CaseCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiCaseService {

    private final AtpProperties properties;
    private final AiCaseGenerator generator;
    private final AiCaseAdapter adapter;
    private final CaseCatalogService caseCatalogService;
    private final TestBrainClient testBrainClient;

    public Map<String, Object> status() {
        AtpProperties.AiCase cfg = properties.getAiCase();
        Map<String, Object> tb = testBrainClient.health();
        boolean tbUp = Boolean.TRUE.equals(tb.get("reachable"));
        boolean rag = Boolean.TRUE.equals(tb.get("rag_knowledge_base"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", cfg.isEnabled());
        m.put("provider", cfg.getProvider());
        m.put("has_llm_key", cfg.getLlmApiKey() != null && !cfg.getLlmApiKey().isBlank());
        m.put("testbrain_url", cfg.getTestbrainUrl());
        m.put("llm_model", cfg.getLlmModel());
        m.put("max_cases", cfg.getMaxCases());
        m.put("confluence_configured",
                (cfg.getConfluenceBaseUrl() != null && !cfg.getConfluenceBaseUrl().isBlank())
                        || (cfg.getConfluenceToken() != null && !cfg.getConfluenceToken().isBlank()));
        m.put("supported_upload_types", java.util.List.of("txt", "md", "docx", "pdf"));
        m.put("testbrain", tb);
        m.put("scope", Map.of(
                "standard_functional_cases", true,
                "rag_knowledge_base", rag,
                "testbrain_deployed", tbUp,
                "automation_step_conversion", false
        ));
        return m;
    }

    public Map<String, Object> generatePreview(Map<String, Object> body) {
        ensureEnabled();
        String prd = str(body.get("prd_text"));
        if (prd.isBlank()) {
            throw new AppException("AI_CASE", "prd_text 不能为空", HttpStatus.BAD_REQUEST);
        }
        String platform = str(body.get("platform"));
        if (platform.isBlank()) platform = "android";
        String appPackage = str(body.get("app_package"));

        JsonNode raw = generator.generate(platform, appPackage, prd);
        List<Map<String, Object>> drafts = adapter.toCaseDrafts(raw, platform, appPackage);

        int totalPending = 0;
        int totalAsserts = 0;
        int ready = 0;
        int issueCases = 0;
        List<String> allIssues = new ArrayList<>();
        for (Map<String, Object> d : drafts) {
            totalPending += num(d.get("pending_steps"));
            totalAsserts += num(d.get("assert_count"));
            if (Boolean.TRUE.equals(d.get("ready_for_editor"))) ready++;
            List<String> issues = AiCaseCompletenessChecker.check(d);
            d.put("review_issues", issues);
            if (!issues.isEmpty()) {
                issueCases++;
                allIssues.add(str(d.get("name")) + "：" + String.join("；", issues));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider_used", raw.path("provider").asText(properties.getAiCase().getProvider()));
        result.put("note", raw.path("note").asText(""));
        result.put("raw", raw);
        result.put("drafts", drafts);
        result.put("count", drafts.size());
        result.put("quality", Map.of(
                "ready_for_editor", ready,
                "pending_steps", totalPending,
                "assert_count", totalAsserts,
                "app_package_set", !appPackage.isBlank()
        ));
        result.put("review_summary", Map.of(
                "cases_with_issues", issueCases,
                "issue_count", allIssues.size(),
                "issues", allIssues.size() > 20 ? allIssues.subList(0, 20) : allIssues
        ));
        return result;
    }

    public Map<String, Object> ingestKnowledge(Map<String, Object> body) {
        ensureEnabled();
        String title = str(body.get("title"));
        String content = str(body.get("content"));
        if (title.isBlank() || content.isBlank()) {
            throw new AppException("AI_CASE", "title 与 content 不能为空", HttpStatus.BAD_REQUEST);
        }
        try {
            Map<String, Object> resp = testBrainClient.ingestKnowledge(title, content);
            if (Boolean.FALSE.equals(resp.get("success")) && resp.get("message") != null) {
                throw new AppException("AI_CASE", String.valueOf(resp.get("message")), HttpStatus.BAD_GATEWAY);
            }
            return resp;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("AI_CASE", "知识库入库失败（请确认 TestBrain 已启动）: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    public Map<String, Object> ingestPrd(Map<String, Object> body) {
        ensureEnabled();
        String prd = str(body.get("prd_text"));
        if (prd.isBlank()) {
            throw new AppException("AI_CASE", "prd_text 不能为空", HttpStatus.BAD_REQUEST);
        }
        String title = str(body.get("title"));
        if (title.isBlank()) {
            title = "PRD-" + System.currentTimeMillis();
        }
        // 过长则截断，避免单条向量 content 超限
        String content = prd.length() > 12000 ? prd.substring(0, 12000) : prd;
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("title", title);
        req.put("content", content);
        Map<String, Object> resp = ingestKnowledge(req);
        resp.put("title", title);
        resp.put("char_count", content.length());
        return resp;
    }

    public Map<String, Object> listKnowledge() {
        ensureEnabled();
        return testBrainClient.listKnowledge();
    }

    public Map<String, Object> importDrafts(Map<String, Object> body, Long userId) {
        ensureEnabled();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> drafts = (List<Map<String, Object>>) body.get("drafts");
        if (drafts == null || drafts.isEmpty()) {
            throw new AppException("AI_CASE", "drafts 不能为空，请先生成预览", HttpStatus.BAD_REQUEST);
        }
        Long folderId = body.get("folder_id") != null ? Long.valueOf(body.get("folder_id").toString()) : null;
        List<Map<String, Object>> createdSummaries = new ArrayList<>();
        List<TestCase> created = new ArrayList<>();
        for (Map<String, Object> draft : drafts) {
            Map<String, Object> payload = new LinkedHashMap<>(draft);
            payload.remove("step_count");
            payload.remove("pending_steps");
            payload.remove("assert_count");
            payload.remove("ui_op_count");
            payload.remove("ready_for_editor");
            payload.remove("case_type");
            payload.remove("test_data");
            payload.remove("standard_steps");
            payload.remove("review_issues");
            if (folderId != null) payload.put("folder_id", folderId);
            payload.putIfAbsent("case_status", "draft");
            payload.putIfAbsent("script_type", "visual");
            TestCase tc = caseCatalogService.createCase(payload, userId);
            created.add(tc);
            Map<String, Object> sum = new LinkedHashMap<>();
            sum.put("id", tc.getId());
            sum.put("name", tc.getName());
            createdSummaries.add(sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", created.size());
        result.put("cases", createdSummaries);
        if (!created.isEmpty()) {
            result.put("first_case_id", created.get(0).getId());
        }
        return result;
    }

    private static int num(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return o == null ? 0 : Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private void ensureEnabled() {
        if (!properties.getAiCase().isEnabled()) {
            throw new AppException("AI_CASE_DISABLED", "AI 用例生成已关闭（atp.ai-case.enabled=false）",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
