package com.atp.platform.service.aicase;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Confluence Cloud / Server 拉取页面正文为纯文本。
 */
@Service
@RequiredArgsConstructor
public class ConfluenceDocService {

    private static final Pattern PAGE_ID = Pattern.compile("(?:pages/|pageId=)(\\d+)");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern HTML_ENTITY = Pattern.compile("&(#?\\w+);");

    private final AtpProperties properties;
    private final ObjectMapper mapper;
    private final RestClient.Builder restClientBuilder;
    private final AiDocumentParseService documentParseService;

    public Map<String, Object> fetchPage(Map<String, Object> body) {
        AtpProperties.AiCase cfg = properties.getAiCase();
        String pageUrl = str(body.get("page_url"));
        String pageId = str(body.get("page_id"));
        String token = str(body.get("token"));
        if (token.isBlank()) token = cfg.getConfluenceToken() == null ? "" : cfg.getConfluenceToken().trim();
        String email = str(body.get("email"));
        if (email.isBlank()) email = cfg.getConfluenceEmail() == null ? "" : cfg.getConfluenceEmail().trim();
        String authType = str(body.get("auth_type"));
        if (authType.isBlank()) authType = cfg.getConfluenceAuthType() == null ? "cloud" : cfg.getConfluenceAuthType();

        if (token.isBlank()) {
            throw new AppException("AI_CASE", "请提供 Confluence Token（请求体 token 或配置 atp.ai-case.confluence-token）",
                    HttpStatus.BAD_REQUEST);
        }
        if (pageId.isBlank()) {
            pageId = extractPageId(pageUrl);
        }
        if (pageId.isBlank()) {
            throw new AppException("AI_CASE", "无法解析 pageId，请填写 page_id 或带 pages/{id} 的页面链接",
                    HttpStatus.BAD_REQUEST);
        }

        String apiRoot = resolveApiRoot(pageUrl, cfg.getConfluenceBaseUrl());
        String uri = apiRoot + "/rest/api/content/" + pageId + "?expand=body.storage,title";

        try {
            RestClient.RequestHeadersSpec<?> spec = restClientBuilder.build()
                    .get()
                    .uri(URI.create(uri))
                    .accept(MediaType.APPLICATION_JSON);
            spec = withAuth(spec, authType, token, email);

            String raw = spec.retrieve().body(String.class);
            JsonNode json = mapper.readTree(raw == null ? "{}" : raw);
            String title = json.path("title").asText("Confluence页面");
            String storage = json.path("body").path("storage").path("value").asText("");
            String text = htmlToText(storage);
            if (text.isBlank()) {
                throw new AppException("AI_CASE", "页面正文为空或无权限读取", HttpStatus.BAD_REQUEST);
            }
            Map<String, Object> out = documentParseService.wrapExternal("confluence:" + title, text);
            out.put("page_id", pageId);
            out.put("title", title);
            return out;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("AI_CASE", "Confluence 拉取失败: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RestClient.RequestHeadersSpec<?> withAuth(
            RestClient.RequestHeadersSpec<?> req, String authType, String token, String email) {
        RestClient.RequestHeadersSpec spec = req;
        if ("basic".equalsIgnoreCase(authType) || "server-basic".equalsIgnoreCase(authType)) {
            if (email.isBlank()) {
                throw new AppException("AI_CASE", "Basic 认证需要 email（Atlassian 账号邮箱）", HttpStatus.BAD_REQUEST);
            }
            String basic = Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
            return spec.header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        }
        if (!email.isBlank()) {
            String basic = Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
            return spec.header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        }
        return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String resolveApiRoot(String pageUrl, String configuredBase) {
        // Cloud: https://xxx.atlassian.net/wiki/rest/api/...
        // Server: https://confluence.company.com/rest/api/...
        if (pageUrl != null && !pageUrl.isBlank()) {
            try {
                URI u = URI.create(pageUrl.trim());
                String host = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
                String path = u.getPath() == null ? "" : u.getPath();
                if (path.contains("/wiki")) {
                    return host + "/wiki";
                }
                return host;
            } catch (Exception ignored) {
                // fall through
            }
        }
        String base = configuredBase == null ? "" : configuredBase.trim();
        if (base.isBlank()) {
            throw new AppException("AI_CASE",
                    "请提供完整 Confluence 页面 URL，或配置 atp.ai-case.confluence-base-url",
                    HttpStatus.BAD_REQUEST);
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    static String extractPageId(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) return "";
        String url = URLDecoder.decode(pageUrl.trim(), StandardCharsets.UTF_8);
        Matcher m = PAGE_ID.matcher(url);
        if (m.find()) return m.group(1);
        return "";
    }

    static String htmlToText(String html) {
        if (html == null || html.isBlank()) return "";
        String s = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)</h[1-6]>", "\n");
        s = HTML_TAG.matcher(s).replaceAll("");
        s = decodeEntities(s);
        return AiDocumentParseService.normalize(s);
    }

    private static String decodeEntities(String s) {
        Matcher m = HTML_ENTITY.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String e = m.group(1);
            String rep = switch (e) {
                case "nbsp" -> " ";
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                default -> {
                    if (e.startsWith("#x") || e.startsWith("#X")) {
                        try {
                            yield String.valueOf((char) Integer.parseInt(e.substring(2), 16));
                        } catch (Exception ex) {
                            yield m.group();
                        }
                    } else if (e.startsWith("#")) {
                        try {
                            yield String.valueOf((char) Integer.parseInt(e.substring(1)));
                        } catch (Exception ex) {
                            yield m.group();
                        }
                    } else yield m.group();
                }
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
