package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.service.CoverageReportService;
import com.atp.platform.service.QueueBoardService;
import com.atp.platform.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ApiResponse<Map<String, Object>> wallboard() {
        return ApiResponse.ok(reportService.wallboard());
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
