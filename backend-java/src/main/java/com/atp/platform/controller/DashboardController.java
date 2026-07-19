package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.service.CoverageReportService;
import com.atp.platform.service.QueueBoardService;
import com.atp.platform.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ReportService reportService;
    private final CoverageReportService coverageReportService;
    private final QueueBoardService queueBoardService;

    @GetMapping
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(reportService.dashboard());
    }

    @GetMapping("/wallboard")
    public ApiResponse<Map<String, Object>> wallboard(
            @RequestParam(name = "time_range", defaultValue = "today") String timeRange,
            @RequestParam(name = "project_code", required = false) String projectCode,
            @RequestParam(name = "team_id", required = false) Long teamId) {
        return ApiResponse.ok(reportService.wallboard(timeRange, projectCode, teamId));
    }

    @GetMapping("/coverage")
    public ApiResponse<Map<String, Object>> coverage() {
        return ApiResponse.ok(coverageReportService.summary());
    }

    @GetMapping("/queue-board")
    public ApiResponse<Map<String, Object>> queueBoard() {
        return ApiResponse.ok(queueBoardService.snapshot());
    }
}
