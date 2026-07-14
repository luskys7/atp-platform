package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.common.PageResult;
import com.atp.platform.entity.*;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseCatalogService caseService;
    private final OfflinePackageService offlinePackageService;

    @GetMapping("/folders/tree")
    public ApiResponse<List<Map<String, Object>>> folderTree() {
        return ApiResponse.ok(caseService.listFolderTree());
    }

    @PostMapping("/folders")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<CaseFolder> createFolder(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(caseService.createFolder(body.get("name").toString(),
                body.get("parent_id") != null ? Long.valueOf(body.get("parent_id").toString()) : null));
    }

    @PutMapping("/folders/reorder")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> reorderFolders(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        caseService.reorderFolders(items);
        return ApiResponse.ok();
    }

    @PutMapping("/folders/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<CaseFolder> updateFolder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long parentId = body.get("parent_id") != null ? Long.valueOf(body.get("parent_id").toString()) : null;
        return ApiResponse.ok(caseService.updateFolder(id, body.get("name").toString(), parentId));
    }

    @DeleteMapping("/folders/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> deleteFolder(@PathVariable Long id) {
        caseService.deleteFolder(id);
        return ApiResponse.ok();
    }

    @GetMapping
    public ApiResponse<PageResult<TestCase>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "folder_id", required = false) Long folderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        Page<TestCase> result = caseService.listCases(page, pageSize, folderId, status, keyword);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<TestCase> get(@PathVariable Long id) {
        return ApiResponse.ok(caseService.getCase(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestCase> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(caseService.createCase(body, SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestCase> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(caseService.updateCase(id, body, SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        caseService.deleteCase(id, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> transfer(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<?>) body.get("ids")).stream()
                .map(o -> Long.valueOf(o.toString()))
                .toList();
        Long ownerId = Long.valueOf(body.get("owner_id").toString());
        return ApiResponse.ok(caseService.transferCases(ids, ownerId));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<TestCaseVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(caseService.listVersions(id));
    }

    @GetMapping("/{id}/versions/compare")
    public ApiResponse<Map<String, Object>> compareVersions(
            @PathVariable Long id,
            @RequestParam(name = "version_a") Long versionA,
            @RequestParam(name = "version_b") Long versionB) {
        return ApiResponse.ok(caseService.compareVersions(id, versionA, versionB));
    }

    @PostMapping("/{id}/rollback/{versionId}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestCase> rollback(@PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.ok(caseService.rollback(id, versionId, SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestTask> run(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        List<Long> deviceIds = parseDeviceIds(body);
        String appPackage = body != null && body.get("app_package") != null
                ? body.get("app_package").toString().trim() : null;
        return ApiResponse.ok(caseService.runCase(id, SecurityUtils.currentUserId(), deviceIds, appPackage));
    }

    @SuppressWarnings("unchecked")
    private static List<Long> parseDeviceIds(Map<String, Object> body) {
        if (body == null) return List.of();
        Object raw = body.get("device_ids");
        if (raw == null) raw = body.get("deviceIds");
        if (raw == null && body.get("device_id") != null) raw = List.of(body.get("device_id"));
        if (raw instanceof List<?> list) {
            return list.stream().map(o -> Long.valueOf(o.toString())).toList();
        }
        if (raw instanceof Number n) {
            return List.of(n.longValue());
        }
        return List.of();
    }

    @GetMapping("/{id}/dependencies")
    public ApiResponse<Map<String, Object>> dependencies(@PathVariable Long id) {
        return ApiResponse.ok(caseService.dependencyCheck(id));
    }

    @PostMapping("/{id}/submit-review")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestCase> submitReview(@PathVariable Long id) {
        return ApiResponse.ok(caseService.submitReview(id, SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<TestCase> approve(@PathVariable Long id) {
        return ApiResponse.ok(caseService.approveCase(id, SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<TestCase> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(caseService.rejectCase(id, body.get("reason"), SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/offline-package")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> offlinePackage(@PathVariable Long id) {
        return ApiResponse.ok(offlinePackageService.buildForCase(id));
    }
}
