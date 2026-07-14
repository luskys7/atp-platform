package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.SuiteRun;
import com.atp.platform.entity.TestTask;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.CheckpointService;
import com.atp.platform.service.SuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CheckpointController {

    private final CheckpointService checkpointService;
    private final SuiteService suiteService;

    @GetMapping("/suites/{suiteId}/runs")
    public ApiResponse<List<SuiteRun>> listRuns(@PathVariable Long suiteId) {
        return ApiResponse.ok(suiteService.listRuns(suiteId));
    }

    @GetMapping("/suite-runs/{runId}")
    public ApiResponse<Map<String, Object>> getRun(@PathVariable Long runId) {
        return ApiResponse.ok(checkpointService.getRunDetail(runId));
    }

    @PostMapping("/suite-runs/{runId}/resume")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> resumeRun(@PathVariable Long runId) {
        return ApiResponse.ok(checkpointService.resumeSuiteRun(runId, SecurityUtils.currentUserId()));
    }

    @PostMapping("/suite-runs/{runId}/pause")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> pauseRun(@PathVariable Long runId) {
        checkpointService.pauseSuiteRun(runId);
        return ApiResponse.ok();
    }

    @PostMapping("/tasks/{id}/resume")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<TestTask> resumeTask(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Integer fromStep = null;
        if (body != null && body.get("from_step") != null) {
            fromStep = Integer.valueOf(body.get("from_step").toString());
        }
        return ApiResponse.ok(checkpointService.resumeTask(id, fromStep, SecurityUtils.currentUserId()));
    }
}
