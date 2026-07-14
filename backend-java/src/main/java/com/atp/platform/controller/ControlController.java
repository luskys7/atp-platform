package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.common.PageResult;
import com.atp.platform.dto.CreateControlPoolRequest;
import com.atp.platform.entity.CaseHealingRecord;
import com.atp.platform.entity.ControlChangeLog;
import com.atp.platform.entity.ControlPool;
import com.atp.platform.entity.ControlPoolVersion;
import com.atp.platform.entity.PrivateControlBinding;
import com.atp.platform.exception.AppException;
import com.atp.platform.exception.ErrorCodes;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.AiLocateService;
import com.atp.platform.service.ControlGovernanceService;
import com.atp.platform.service.ControlPoolService;
import com.atp.platform.service.ControlResolverService;
import com.atp.platform.service.LocatorFailureReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/controls")
@RequiredArgsConstructor
public class ControlController {

    private final ControlPoolService controlPoolService;
    private final ControlResolverService controlResolverService;
    private final ControlGovernanceService controlGovernanceService;
    private final LocatorFailureReportService failureReportService;
    private final AiLocateService aiLocateService;

    @GetMapping("/pool")
    public ApiResponse<PageResult<ControlPool>> listPool(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "app_package", required = false) String appPackage,
            @RequestParam(name = "page_name", required = false) String pageName,
            @RequestParam(required = false) String platform,
            @RequestParam(name = "version_tag", required = false) String versionTag,
            @RequestParam(name = "env_tag", required = false) String envTag,
            @RequestParam(name = "team_id", required = false) Long teamId,
            @RequestParam(required = false) String status) {
        Page<ControlPool> result = controlPoolService.list(
                page, pageSize, appPackage, pageName, platform, versionTag, envTag, teamId, status);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @PostMapping("/pool")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<ControlPool> createPool(@Valid @RequestBody CreateControlPoolRequest req) {
        return ApiResponse.ok(controlPoolService.create(req, SecurityUtils.currentUserId()));
    }

    @PutMapping("/pool/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<ControlPool> updatePool(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean propagate = body.get("propagate_bindings") != null
                && Boolean.parseBoolean(body.get("propagate_bindings").toString());
        return ApiResponse.ok(controlPoolService.updatePool(id, body, propagate, SecurityUtils.currentUserId()));
    }

    @GetMapping("/pool/{id}/dependencies")
    public ApiResponse<Map<String, Object>> scanDependencies(@PathVariable Long id) {
        return ApiResponse.ok(controlPoolService.scanDependencies(id));
    }

    @GetMapping("/pool/{id}/change-logs")
    public ApiResponse<PageResult<ControlChangeLog>> changeLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        var result = controlPoolService.changeLogs(id, page, pageSize);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/pool/{id}/versions")
    public ApiResponse<List<ControlPoolVersion>> listVersions(@PathVariable Long id) {
        return ApiResponse.ok(controlPoolService.listVersions(id));
    }

    @PostMapping("/pool/{id}/rollback/{versionId}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<ControlPool> rollback(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean propagate = body != null && body.get("propagate_bindings") != null
                && Boolean.parseBoolean(body.get("propagate_bindings").toString());
        return ApiResponse.ok(controlPoolService.rollbackToVersion(id, versionId, propagate, SecurityUtils.currentUserId()));
    }

    @GetMapping("/lookup")
    public ApiResponse<ControlPool> lookup(
            @RequestParam(name = "app_package") String appPackage,
            @RequestParam(name = "element_name") String elementName) {
        return ApiResponse.ok(controlPoolService.lookup(appPackage, elementName)
                .orElseThrow(() -> new AppException(ErrorCodes.E4001, HttpStatus.NOT_FOUND)));
    }

    @PostMapping("/resolve")
    public ApiResponse<ControlPoolService.ResolvedControl> resolve(@RequestBody Map<String, Object> body) {
        Long taskId = body.get("task_id") != null ? ((Number) body.get("task_id")).longValue() : null;
        Integer stepIndex = body.get("step_index") != null ? ((Number) body.get("step_index")).intValue() : null;
        String elementName = (String) body.get("element_name");
        String appPackage = (String) body.get("app_package");
        return ApiResponse.ok(controlResolverService.resolve(taskId, stepIndex, elementName, appPackage));
    }

    @PostMapping("/private-bindings")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<PrivateControlBinding> createBinding(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(controlPoolService.createPrivateBinding(
                ((Number) body.get("task_id")).longValue(),
                body.get("step_index") != null ? ((Number) body.get("step_index")).intValue() : null,
                (String) body.get("element_name"),
                (String) body.get("locator_type"),
                (String) body.get("locator_value"),
                body.get("pool_id") != null ? ((Number) body.get("pool_id")).longValue() : null,
                SecurityUtils.currentUserId()
        ));
    }

    @GetMapping("/private-bindings/{taskId}")
    public ApiResponse<List<PrivateControlBinding>> getBindings(@PathVariable Long taskId) {
        return ApiResponse.ok(controlPoolService.getPrivateBindings(taskId));
    }

    @DeleteMapping("/private-bindings/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> deleteBinding(@PathVariable Long id) {
        controlPoolService.deletePrivateBinding(id, SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/healing-records/{taskId}")
    public ApiResponse<List<CaseHealingRecord>> healingRecords(@PathVariable Long taskId) {
        return ApiResponse.ok(controlPoolService.getHealingRecords(taskId));
    }

    @PostMapping("/ai-locate")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> aiLocate(@RequestBody Map<String, String> body) {
        AiLocateService.AiLocateResult result = aiLocateService.locate(
                body.get("serial_number"),
                body.getOrDefault("platform", "android"),
                body.get("query"),
                body.get("app_package")
        );
        return ApiResponse.ok(Map.of(
                "locator_type", result.locatorType(),
                "locator_value", result.locatorValue(),
                "confidence", result.confidence(),
                "strategy", result.strategy()
        ));
    }

    @GetMapping("/locator-failure-stats")
    public ApiResponse<Map<String, Object>> locatorFailureStats(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(failureReportService.stats(days));
    }

    @PostMapping("/batch-validate")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> batchValidate(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(controlGovernanceService.batchValidate(body, SecurityUtils.currentUserId()));
    }

    @GetMapping("/unstable-stats")
    public ApiResponse<List<Map<String, Object>>> unstableStats(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(controlGovernanceService.unstableStats(days, limit));
    }

    @PostMapping("/pool/{id}/archive")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<ControlPool> archivePool(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> patch = new java.util.LinkedHashMap<>();
        patch.put("status", "archived");
        patch.put("reason", body != null && body.get("reason") != null ? body.get("reason").toString() : "手动归档");
        boolean propagate = body != null && body.get("propagate_bindings") != null
                && Boolean.parseBoolean(body.get("propagate_bindings").toString());
        return ApiResponse.ok(controlPoolService.updatePool(id, patch, propagate, SecurityUtils.currentUserId()));
    }

    @PostMapping("/pool/batch-replace")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> batchReplace(@RequestBody Map<String, Object> body) {
        String appPackage = body.get("app_package") != null ? body.get("app_package").toString() : "";
        String oldName = body.get("old_name") != null ? body.get("old_name").toString() : "";
        String newName = body.get("new_name") != null ? body.get("new_name").toString() : "";
        boolean updateCases = Boolean.TRUE.equals(body.get("update_cases"));
        return ApiResponse.ok(controlPoolService.batchReplaceElementName(
                appPackage, oldName, newName, updateCases, SecurityUtils.currentUserId()));
    }
}
