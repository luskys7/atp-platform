package com.atp.platform.service;

import com.atp.platform.entity.DataFactoryRun;
import com.atp.platform.entity.DataFactoryTemplate;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.DataFactoryRunRepository;
import com.atp.platform.repository.DataFactoryTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataFactoryService {

    private final DataFactoryTemplateRepository templateRepository;
    private final DataFactoryRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public List<DataFactoryTemplate> list() {
        return templateRepository.findAllByOrderByNameAsc();
    }

    public DataFactoryTemplate get(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "造数模板不存在", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public DataFactoryTemplate create(Map<String, Object> body, Long userId) {
        DataFactoryTemplate t = map(new DataFactoryTemplate(), body);
        t.setCreatedBy(userId);
        return templateRepository.save(t);
    }

    @Transactional
    public DataFactoryTemplate update(Long id, Map<String, Object> body) {
        return templateRepository.save(map(get(id), body));
    }

    /** 供脚本生成器嵌入模板定义 */
    public Map<String, Object> templateDefForScript(Long templateId) {
        DataFactoryTemplate t = get(templateId);
        if (!Boolean.TRUE.equals(t.getEnabled())) {
            throw new AppException("DISABLED", "造数模板已禁用", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("id", t.getId());
        def.put("method", t.getMethod());
        def.put("url", t.getUrlTemplate());
        def.put("headers", objectMapper.convertValue(parseJson(t.getHeadersJson()), Map.class));
        def.put("body", t.getBodyTemplate() != null ? t.getBodyTemplate() : "");
        def.put("extract", objectMapper.convertValue(parseJson(t.getExtractJson()), Map.class));
        return def;
    }

    /** 解析执行日志 ATP_FACTORY_OUT:{json} 并登记清理任务 */
    @Transactional
    public void ingestFactoryOutput(Long taskId, Long suiteRunId, Long templateId, String varsJson) {
        DataFactoryRun run = new DataFactoryRun();
        run.setTemplateId(templateId);
        run.setTaskId(taskId);
        run.setSuiteRunId(suiteRunId);
        run.setCreatedVarsJson(varsJson);
        run.setCleanupStatus(DataFactoryRun.CleanupStatus.pending);
        runRepository.save(run);
    }

    @Transactional
    public int cleanupForTask(Long taskId) {
        List<DataFactoryRun> runs = runRepository.findByTaskIdAndCleanupStatus(taskId, DataFactoryRun.CleanupStatus.pending);
        int done = 0;
        for (DataFactoryRun run : runs) {
            if (executeCleanup(run)) done++;
        }
        return done;
    }

    @Transactional
    public int cleanupForSuiteRun(Long suiteRunId) {
        List<DataFactoryRun> runs = runRepository.findBySuiteRunIdAndCleanupStatus(suiteRunId, DataFactoryRun.CleanupStatus.pending);
        int done = 0;
        for (DataFactoryRun run : runs) {
            if (executeCleanup(run)) done++;
        }
        return done;
    }

    private boolean executeCleanup(DataFactoryRun run) {
        DataFactoryTemplate tpl = templateRepository.findById(run.getTemplateId()).orElse(null);
        if (tpl == null || tpl.getCleanupUrlTemplate() == null || tpl.getCleanupUrlTemplate().isBlank()) {
            run.setCleanupStatus(DataFactoryRun.CleanupStatus.skipped);
            run.setCleanupMessage("无清理配置");
            run.setCleanedAt(LocalDateTime.now());
            runRepository.save(run);
            return false;
        }
        try {
            Map<String, String> vars = parseVarsMap(run.getCreatedVarsJson());
            String url = substitute(tpl.getCleanupUrlTemplate(), vars);
            String body = tpl.getCleanupBodyTemplate() != null
                    ? substitute(tpl.getCleanupBodyTemplate(), vars) : "";
            String method = tpl.getCleanupMethod() != null ? tpl.getCleanupMethod() : "DELETE";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));
            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else if ("DELETE".equalsIgnoreCase(method)) {
                builder.method("DELETE", body.isBlank() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method.toUpperCase(), HttpRequest.BodyPublishers.ofString(body));
            }
            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                run.setCleanupStatus(DataFactoryRun.CleanupStatus.done);
                run.setCleanupMessage("HTTP " + resp.statusCode());
            } else {
                run.setCleanupStatus(DataFactoryRun.CleanupStatus.failed);
                run.setCleanupMessage("HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            run.setCleanupStatus(DataFactoryRun.CleanupStatus.failed);
            run.setCleanupMessage(e.getMessage());
            log.warn("data factory cleanup failed run={}: {}", run.getId(), e.getMessage());
        }
        run.setCleanedAt(LocalDateTime.now());
        runRepository.save(run);
        return run.getCleanupStatus() == DataFactoryRun.CleanupStatus.done;
    }

    private Map<String, String> parseVarsMap(String json) {
        Map<String, String> m = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return m;
        try {
            JsonNode node = objectMapper.readTree(json);
            node.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asText("")));
        } catch (Exception ignored) {
        }
        return m;
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json != null && !json.isBlank() ? json : "{}");
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String substitute(String text, Map<String, String> vars) {
        String r = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            r = r.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return r;
    }

    private DataFactoryTemplate map(DataFactoryTemplate t, Map<String, Object> body) {
        if (body.containsKey("name")) t.setName(body.get("name").toString());
        if (body.containsKey("description")) t.setDescription(str(body.get("description")));
        if (body.containsKey("method")) t.setMethod(body.get("method").toString());
        if (body.containsKey("url_template")) t.setUrlTemplate(body.get("url_template").toString());
        if (body.containsKey("headers_json")) t.setHeadersJson(str(body.get("headers_json")));
        if (body.containsKey("body_template")) t.setBodyTemplate(str(body.get("body_template")));
        if (body.containsKey("extract_json")) t.setExtractJson(str(body.get("extract_json")));
        if (body.containsKey("cleanup_method")) t.setCleanupMethod(str(body.get("cleanup_method")));
        if (body.containsKey("cleanup_url_template")) t.setCleanupUrlTemplate(str(body.get("cleanup_url_template")));
        if (body.containsKey("cleanup_body_template")) t.setCleanupBodyTemplate(str(body.get("cleanup_body_template")));
        if (body.containsKey("enabled") && body.get("enabled") != null) {
            t.setEnabled(Boolean.parseBoolean(body.get("enabled").toString()));
        }
        return t;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
