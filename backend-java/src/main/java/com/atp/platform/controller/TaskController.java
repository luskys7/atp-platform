package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.common.PageResult;
import com.atp.platform.dto.CreateTaskRequest;
import com.atp.platform.entity.ExecutionLog;
import com.atp.platform.entity.TaskExecution;
import com.atp.platform.entity.TestTask;
import com.atp.platform.entity.User;
import com.atp.platform.exception.AppException;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.AuthService;
import com.atp.platform.service.DefectLinkService;
import com.atp.platform.service.ExecutionStepMarkerService;
import com.atp.platform.service.OfflinePackageService;
import com.atp.platform.service.SchedulerService;
import com.atp.platform.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final SchedulerService schedulerService;
    private final AuthService authService;
    private final DefectLinkService defectLinkService;
    private final OfflinePackageService offlinePackageService;
    private final ExecutionStepMarkerService executionStepMarkerService;

    @GetMapping
    public ApiResponse<PageResult<TestTask>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        Long createdBy = null;
        // 简化：由前端按角色过滤，此处返回全部或按创建人
        Page<TestTask> result = taskService.list(page, pageSize, status, createdBy);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestTask> create(@Valid @RequestBody CreateTaskRequest req) {
        return ApiResponse.ok(taskService.create(req, SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TestTask> get(@PathVariable Long id) {
        return ApiResponse.ok(taskService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestTask> update(@PathVariable Long id, @Valid @RequestBody CreateTaskRequest req) {
        return ApiResponse.ok(taskService.update(id, req));
    }

    @PostMapping("/visual/preview")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, String>> previewVisual(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(Map.of("script", taskService.previewVisualScript(body.get("visual_json"))));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, String>> submit(@PathVariable Long id) {
        TestTask task = taskService.getById(id);
        taskService.submit(id);
        schedulerService.enqueueTask(id, task.getPriority());
        return ApiResponse.ok(Map.of("message", "任务已提交调度"));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        schedulerService.cancelTask(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/queue/pause")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> pause(@PathVariable Long id) {
        schedulerService.pauseTask(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/queue/resume")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, String>> resumeQueue(@PathVariable Long id) throws AppException {
        schedulerService.resumeTask(id);
        return ApiResponse.ok(Map.of("message", "任务已恢复"));
    }

    @PostMapping("/{id}/queue/pin")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, String>> pin(@PathVariable Long id) throws AppException {
        schedulerService.pinTask(id);
        return ApiResponse.ok(Map.of("message", "任务已置顶"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        taskService.delete(id);
        authService.createAuditLog(SecurityUtils.currentUserId(), "delete", "task", id.toString(), "", request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/executions")
    public ApiResponse<List<TaskExecution>> executions(@PathVariable Long id) {
        return ApiResponse.ok(taskService.getExecutions(id));
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<ExecutionLog>> logs(@PathVariable Long id,
            @RequestParam(name = "log_type", required = false) String logType,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(taskService.getLogs(id, logType, keyword));
    }

    @GetMapping("/{id}/defect-info")
    public ApiResponse<Map<String, Object>> defectInfo(@PathVariable Long id) {
        return ApiResponse.ok(defectLinkService.getDefectInfo(id));
    }

    @PostMapping("/{id}/offline-package")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> offlinePackage(@PathVariable Long id) {
        return ApiResponse.ok(offlinePackageService.buildForTask(taskService.getById(id)));
    }

    @GetMapping("/{id}/failure-playback")
    public ApiResponse<Map<String, Object>> failurePlayback(@PathVariable Long id) {
        return ApiResponse.ok(executionStepMarkerService.failurePlayback(id));
    }

    @GetMapping("/{id}/step-markers")
    public ApiResponse<List<com.atp.platform.entity.ExecutionStepMarker>> stepMarkers(@PathVariable Long id) {
        return ApiResponse.ok(executionStepMarkerService.listByTask(id));
    }
}
