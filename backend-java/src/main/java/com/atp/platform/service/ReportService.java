package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.*;
import com.atp.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TestReportRepository reportRepository;
    private final TaskExecutionRepository executionRepository;
    private final RecordingResourceRepository recordingRepository;
    private final ExecutionLogRepository logRepository;
    private final DeviceRepository deviceRepository;
    private final TestTaskRepository taskRepository;
    private final PdfReportService pdfReportService;
    private final ExcelReportService excelReportService;
    private final AtpProperties properties;
    private final TaskQueueService taskQueue;

    public Page<TestReport> list(int page, int pageSize) {
        return reportRepository.findAll(PageRequest.of(page - 1, pageSize));
    }

    public Map<String, Object> getDetail(Long taskId) {
        TestReport report = reportRepository.findByTaskId(taskId)
                .orElseThrow();
        Map<String, Object> detail = new HashMap<>();
        detail.put("report", report);
        detail.put("executions", executionRepository.findByTaskId(taskId));
        detail.put("recordings", recordingRepository.findByTaskIdOrderByCreatedAtDesc(taskId));
        detail.put("logs", logRepository.findByTaskIdOrderByCreatedAtAsc(taskId));
        return detail;
    }

    public String exportPdf(Long taskId) {
        TestReport report = reportRepository.findByTaskId(taskId).orElseThrow();
        try {
            String path = pdfReportService.generate(report);
            report.setPdfPath(path);
            reportRepository.save(report);
            return path;
        } catch (Exception e) {
            throw new RuntimeException("PDF 导出失败: " + e.getMessage(), e);
        }
    }

    public String exportExcel(Long taskId) {
        TestReport report = reportRepository.findByTaskId(taskId).orElseThrow();
        try {
            return excelReportService.generate(report);
        } catch (Exception e) {
            throw new RuntimeException("Excel 导出失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("devices", Map.of(
                "total", deviceRepository.count(),
                "online", deviceRepository.countByStatus(Device.DeviceStatus.online),
                "busy", deviceRepository.countByStatus(Device.DeviceStatus.busy)
        ));
        long success = taskRepository.countByStatus(TestTask.TaskStatus.success);
        long failed = taskRepository.countByStatus(TestTask.TaskStatus.failed);
        double passRate = (success + failed) > 0 ? (double) success / (success + failed) * 100 : 0;
        stats.put("tasks", Map.of(
                "total", taskRepository.count(),
                "running", taskRepository.countByStatus(TestTask.TaskStatus.running),
                "success", success,
                "failed", failed
        ));
        stats.put("pass_rate", passRate);
        stats.put("min_pass_rate", properties.getGovernance().getMinPassRate());
        stats.put("pass_rate_ok", passRate >= properties.getGovernance().getMinPassRate());

        List<TestReport> recent = reportRepository.findTop10ByOrderByCreatedAtDesc();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            TestReport r = recent.get(i);
            trend.add(Map.of(
                    "task_id", r.getTaskId(),
                    "pass_rate", r.getPassRate() != null ? r.getPassRate() : 0,
                    "title", r.getTitle() != null ? r.getTitle() : "",
                    "created_at", r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""
            ));
        }
        stats.put("pass_rate_trend", trend);
        return stats;
    }

    public Map<String, Object> wallboard() {
        Map<String, Object> row = new LinkedHashMap<>();
        long online = deviceRepository.countByStatus(Device.DeviceStatus.online);
        long busy = deviceRepository.countByStatus(Device.DeviceStatus.busy);
        long offline = deviceRepository.countByStatus(Device.DeviceStatus.offline);
        long error = deviceRepository.countByStatus(Device.DeviceStatus.error);
        long totalDevices = deviceRepository.count();

        long running = taskRepository.countByStatus(TestTask.TaskStatus.running);
        long queued = taskRepository.countByStatus(TestTask.TaskStatus.queued);
        long pending = taskRepository.countByStatus(TestTask.TaskStatus.pending);
        long success = taskRepository.countByStatus(TestTask.TaskStatus.success);
        long failed = taskRepository.countByStatus(TestTask.TaskStatus.failed);
        double passRate = (success + failed) > 0 ? (double) success / (success + failed) * 100 : 0;

        row.put("checked_at", java.time.LocalDateTime.now().toString());
        row.put("devices", Map.of(
                "total", totalDevices,
                "online", online,
                "busy", busy,
                "offline", offline,
                "error", error
        ));
        row.put("tasks", Map.of(
                "running", running,
                "queued", queued,
                "pending", pending,
                "success", success,
                "failed", failed
        ));
        row.put("queue_depth", taskQueue.size());
        row.put("pass_rate", passRate);
        row.put("min_pass_rate", properties.getGovernance().getMinPassRate());
        row.put("pass_rate_ok", passRate >= properties.getGovernance().getMinPassRate());

        List<TestTask> runningTasks = taskRepository.findByStatusOrderByStartedAtDesc(TestTask.TaskStatus.running);
        List<Map<String, Object>> active = new ArrayList<>();
        for (TestTask t : runningTasks.stream().limit(8).toList()) {
            active.add(Map.of(
                    "id", t.getId(),
                    "name", t.getName() != null ? t.getName() : "",
                    "platform", t.getPlatform() != null ? t.getPlatform().name() : "",
                    "started_at", t.getStartedAt() != null ? t.getStartedAt().toString() : ""
            ));
        }
        row.put("running_tasks", active);
        row.put("pass_rate_trend", dashboard().get("pass_rate_trend"));
        return row;
    }
}
