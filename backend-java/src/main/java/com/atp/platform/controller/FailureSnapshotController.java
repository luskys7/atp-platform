package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.FailureSnapshot;
import com.atp.platform.service.FailureSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class FailureSnapshotController {

    private final FailureSnapshotService service;

    @GetMapping("/{taskId}/failure-snapshots")
    public ApiResponse<List<FailureSnapshot>> list(@PathVariable Long taskId) {
        return ApiResponse.ok(service.listByTask(taskId));
    }
}
