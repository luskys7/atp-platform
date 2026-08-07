package com.atp.platform.service.aicase;

import com.atp.platform.config.AtpProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TestBrain 外挂 HTTP 客户端（独立部署服务，零底层侵入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestBrainClient {

    private final AtpProperties properties;
    private final ObjectMapper mapper;

    public Map<String, Object> health() {
        try {
            String raw = client().get()
                    .uri("/api/v1/health")
                    .retrieve()
                    .body(String.class);
            JsonNode n = mapper.readTree(raw == null ? "{}" : raw);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reachable", true);
            m.put("status", n.path("status").asText("unknown"));
            m.put("milvus", n.path("milvus").asBoolean(false));
            m.put("rag_knowledge_base", n.path("rag_knowledge_base").asBoolean(false));
            m.put("has_llm_key", n.path("has_llm_key").asBoolean(false));
            m.put("llm_provider", n.path("llm_provider").asText(""));
            m.put("embedding_provider", n.path("embedding_provider").asText(""));
            m.put("milvus_detail", n.path("milvus_detail").asText(""));
            return m;
        } catch (Exception e) {
            // 远程原生 TestBrain 可能无 /api/v1/health：回退探测首页
            try {
                client().get().uri("/").retrieve().toBodilessEntity();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("reachable", true);
                m.put("status", "up");
                m.put("milvus", false);
                m.put("rag_knowledge_base", false);
                m.put("probe", "root");
                m.put("health_api_error", e.getMessage());
                return m;
            } catch (Exception e2) {
                log.debug("TestBrain health 不可达: {} / {}", e.getMessage(), e2.getMessage());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("reachable", false);
                m.put("status", "down");
                m.put("milvus", false);
                m.put("rag_knowledge_base", false);
                m.put("error", e2.getMessage());
                return m;
            }
        }
    }

    public JsonNode generateCases(String platform, String appPackage, String requirement, int maxCases) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requirement", requirement == null ? "" : requirement);
        body.put("platform", platform == null ? "android" : platform);
        body.put("app_package", appPackage == null ? "" : appPackage);
        body.put("max_cases", maxCases);
        body.put("schema", "atp_standard_case_v1");
        String raw = postJson("/api/v1/ai/generate-cases", body);
        try {
            return mapper.readTree(raw == null ? "{}" : raw);
        } catch (Exception e) {
            throw new IllegalStateException("TestBrain 返回非 JSON: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> ingestKnowledge(String title, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title == null ? "" : title);
        body.put("content", content == null ? "" : content);
        String raw = postJson("/api/v1/ai/knowledge/ingest", body);
        return parseMap(raw);
    }

    public Map<String, Object> listKnowledge() {
        try {
            String raw = client().get()
                    .uri("/api/v1/ai/knowledge/list")
                    .retrieve()
                    .body(String.class);
            return parseMap(raw);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", false);
            m.put("message", e.getMessage());
            m.put("knowledge_items", Collections.emptyList());
            m.put("count", 0);
            return m;
        }
    }

    private String postJson(String uri, Map<String, Object> body) {
        try {
            byte[] json = mapper.writeValueAsBytes(body);
            return client().post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            String resp = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            log.warn("TestBrain {} HTTP {}: {}", uri, e.getStatusCode().value(), resp);
            // 把对端 JSON 透出，便于前端展示真实原因
            if (resp != null && !resp.isBlank()) {
                return resp;
            }
            throw new IllegalStateException("TestBrain HTTP " + e.getStatusCode().value() + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用 TestBrain 失败: " + e.getMessage(), e);
        }
    }

    private RestClient client() {
        AtpProperties.AiCase cfg = properties.getAiCase();
        String base = trimSlash(cfg.getTestbrainUrl());
        int timeoutSec = Math.max(10, cfg.getTimeoutSeconds());
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Math.min(30_000, timeoutSec * 1000));
        rf.setReadTimeout(timeoutSec * 1000);
        return RestClient.builder()
                .baseUrl(base)
                .requestFactory(rf)
                .build();
    }

    private Map<String, Object> parseMap(String raw) {
        try {
            return mapper.readValue(raw == null ? "{}" : raw, new TypeReference<>() {});
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", false);
            m.put("message", "解析失败: " + e.getMessage());
            m.put("raw", raw);
            return m;
        }
    }

    private static String trimSlash(String u) {
        if (u == null || u.isBlank()) return "http://10.0.98.20:8000";
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u.trim();
    }
}
