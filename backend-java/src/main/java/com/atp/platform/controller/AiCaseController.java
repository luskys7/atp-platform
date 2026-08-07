package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.aicase.AiCaseService;
import com.atp.platform.service.aicase.AiDocumentParseService;
import com.atp.platform.service.aicase.ConfluenceDocService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * AI 用例生成外挂入口。
 * 独立模块：不与设备/录制/套件/执行等能力互通；可选单向落库草稿。
 */
@RestController
@RequestMapping("/api/v1/ai-cases")
@RequiredArgsConstructor
public class AiCaseController {

    private final AiCaseService aiCaseService;
    private final AiDocumentParseService documentParseService;
    private final ConfluenceDocService confluenceDocService;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(aiCaseService.status());
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiCaseService.generatePreview(body));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> importDrafts(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiCaseService.importDrafts(body, SecurityUtils.currentUserId()));
    }

    @PostMapping(value = "/parse-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> parseDocument(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(documentParseService.parseUpload(file));
    }

    @PostMapping("/fetch-confluence")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> fetchConfluence(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(confluenceDocService.fetchPage(body));
    }

    @PostMapping("/knowledge/ingest")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> ingestKnowledge(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiCaseService.ingestKnowledge(body));
    }

    @PostMapping("/knowledge/ingest-prd")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> ingestPrd(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiCaseService.ingestPrd(body));
    }

    @GetMapping("/knowledge/list")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> listKnowledge() {
        return ApiResponse.ok(aiCaseService.listKnowledge());
    }
}
