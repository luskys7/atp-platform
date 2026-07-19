package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.*;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ReportService {

    public static final int DEFAULT_RETAIN_DAYS = 90;
    public static final int EXPIRE_WARN_DAYS = 7;

    private final TestReportRepository reportRepository;
    private final TaskExecutionRepository executionRepository;
    private final RecordingResourceRepository recordingRepository;
    private final ExecutionLogRepository logRepository;
    private final DeviceRepository deviceRepository;
    private final TestTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PdfReportService pdfReportService;
    private final ExcelReportService excelReportService;
    private final AtpProperties properties;
    private final TaskQueueService taskQueue;

    public Page<TestReport> list(int page, int pageSize) {
        return listAdvanced(page, pageSize, Map.of());
    }

    public Page<TestReport> listAdvanced(int page, int pageSize, Map<String, Object> filters) {
        return reportRepository.findAll(buildSpec(filters), PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1)));
    }

    public Map<String, Object> facets() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modules", reportRepository.distinctModules());
        out.put("versions", reportRepository.distinctVersions());
        out.put("retain_days", retainDays());
        return out;
    }

    public Map<String, Object> stats(Map<String, Object> filters) {
        List<TestReport> all = reportRepository.findAll(buildSpec(filters));
        long reportCount = all.size();
        long totalCases = 0;
        long failedCases = 0;
        double weightedPass = 0;
        long weight = 0;
        for (TestReport r : all) {
            int total = r.getTotalExecutions() != null ? r.getTotalExecutions() : 0;
            int failed = r.getFailedCount() != null ? r.getFailedCount() : 0;
            totalCases += total;
            failedCases += failed;
            if (total > 0 && r.getPassRate() != null) {
                weightedPass += r.getPassRate() * total;
                weight += total;
            }
        }
        double avgPass = weight > 0 ? weightedPass / weight : 0;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report_count", reportCount);
        out.put("total_cases", totalCases);
        out.put("avg_pass_rate", Math.round(avgPass * 10) / 10.0);
        out.put("failed_cases", failedCases);
        out.put("pass_rate_alert", avgPass < 90);
        out.put("failed_alert", failedCases > 0);
        out.put("retain_days", retainDays());
        return out;
    }

    public List<Map<String, Object>> toViewList(List<TestReport> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TestReport r : rows) {
            list.add(toView(r));
        }
        return list;
    }

    public Map<String, Object> toView(TestReport r) {
        if (needsEnrich(r) && r.getTaskId() != null) {
            taskRepository.findById(r.getTaskId()).ifPresent(task -> enrichFromTask(r, task));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("task_id", r.getTaskId());
        m.put("title", r.getTitle());
        m.put("total_executions", r.getTotalExecutions());
        m.put("success_count", r.getSuccessCount());
        m.put("failed_count", r.getFailedCount());
        m.put("pass_rate", r.getPassRate());
        m.put("summary", r.getSummary());
        m.put("module_name", r.getModuleName());
        m.put("version_label", r.getVersionLabel());
        m.put("project_code", r.getProjectCode());
        m.put("operator_label", r.getOperatorLabel());
        m.put("device_label", r.getDeviceLabel());
        m.put("archived", Boolean.TRUE.equals(r.getArchived()));
        m.put("created_at", r.getCreatedAt());
        m.put("updated_at", r.getUpdatedAt());

        String display = resolveDisplayStatus(r);
        m.put("display_status", display);
        m.put("display_status_label", displayStatusLabel(display));
        long ageDays = ageDays(r);
        int retain = retainDays();
        long daysLeft = retain - ageDays;
        boolean archived = Boolean.TRUE.equals(r.getArchived());
        m.put("age_days", ageDays);
        m.put("days_left", daysLeft);
        m.put("expire_soon", !archived && daysLeft <= EXPIRE_WARN_DAYS);
        m.put("expired", !archived && daysLeft <= 0);
        m.put("retain_days", retain);
        m.put("low_pass", r.getPassRate() != null && r.getPassRate() < 90);
        m.put("full_pass", r.getPassRate() != null && r.getPassRate() >= 100);
        return m;
    }

    private boolean needsEnrich(TestReport r) {
        return blank(r.getOperatorLabel()) || blank(r.getDeviceLabel())
                || blank(r.getModuleName()) || blank(r.getProjectCode());
    }

    public Map<String, Object> getDetail(Long taskId) {
        TestReport report = reportRepository.findByTaskId(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "报告不存在", HttpStatus.NOT_FOUND));
        Map<String, Object> detail = new HashMap<>();
        detail.put("report", toView(report));
        detail.put("executions", executionRepository.findByTaskId(taskId));
        detail.put("recordings", recordingRepository.findByTaskIdOrderByCreatedAtDesc(taskId));
        detail.put("logs", logRepository.findByTaskIdOrderByCreatedAtAsc(taskId));
        return detail;
    }

    public String exportPdf(Long taskId) {
        TestReport report = reportRepository.findByTaskId(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "报告不存在", HttpStatus.NOT_FOUND));
        try {
            String path = pdfReportService.generate(report);
            report.setPdfPath(path);
            reportRepository.save(report);
            return path;
        } catch (Exception e) {
            throw new AppException("E2002", "PDF 导出失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String exportExcel(Long taskId) {
        TestReport report = reportRepository.findByTaskId(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "报告不存在", HttpStatus.NOT_FOUND));
        try {
            return excelReportService.generate(report);
        } catch (Exception e) {
            throw new AppException("E2002", "Excel 导出失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public Map<String, Object> archive(Long id) {
        TestReport report = get(id);
        report.setArchived(true);
        reportRepository.save(report);
        return toView(report);
    }

    @Transactional
    public Map<String, Object> batchArchive(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new AppException("INVALID", "请选择要归档的报告", HttpStatus.BAD_REQUEST);
        }
        int n = 0;
        for (Long id : ids) {
            var opt = reportRepository.findById(id);
            if (opt.isPresent()) {
                TestReport r = opt.get();
                r.setArchived(true);
                reportRepository.save(r);
                n++;
            }
        }
        return Map.of("archived", n);
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        TestReport report = get(id);
        deleteReportFiles(report);
        reportRepository.delete(report);
        return Map.of("deleted_id", id);
    }

    @Transactional
    public Map<String, Object> batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new AppException("INVALID", "请选择要删除的报告", HttpStatus.BAD_REQUEST);
        }
        int n = 0;
        for (Long id : ids) {
            var opt = reportRepository.findById(id);
            if (opt.isPresent()) {
                deleteReportFiles(opt.get());
                reportRepository.delete(opt.get());
                n++;
            }
        }
        return Map.of("deleted", n);
    }

    /** 清理超出留存周期且未归档的报告；ids 为空则清理全部过期 */
    @Transactional
    public Map<String, Object> purgeExpired(List<Long> ids) {
        LocalDateTime expireBefore = LocalDate.now().minusDays(retainDays()).atStartOfDay();
        List<TestReport> candidates;
        if (ids != null && !ids.isEmpty()) {
            candidates = reportRepository.findAllById(ids);
        } else {
            candidates = reportRepository.findAll((root, q, cb) -> cb.and(
                    cb.or(cb.isNull(root.get("archived")), cb.isFalse(root.get("archived"))),
                    cb.lessThanOrEqualTo(root.get("createdAt"), expireBefore)
            ));
        }
        int n = 0;
        for (TestReport r : candidates) {
            if (Boolean.TRUE.equals(r.getArchived())) continue;
            if (r.getCreatedAt() != null && r.getCreatedAt().isAfter(expireBefore)) continue;
            deleteReportFiles(r);
            reportRepository.delete(r);
            n++;
        }
        return Map.of("deleted", n);
    }

    public void writeBatchExportZip(List<Long> ids, OutputStream out) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new AppException("INVALID", "请选择要导出的报告", HttpStatus.BAD_REQUEST);
        }
        Path reportsRoot = Path.of(properties.getStorage().getReportsPath()).toAbsolutePath().normalize();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            int added = 0;
            for (Long id : ids) {
                TestReport report = reportRepository.findById(id).orElse(null);
                if (report == null || report.getTaskId() == null) continue;
                Long taskId = report.getTaskId();
                try {
                    String pdfRel = exportPdf(taskId);
                    String xlsRel = exportExcel(taskId);
                    Path pdf = resolveReportFile(reportsRoot, pdfRel);
                    Path xls = resolveReportFile(reportsRoot, xlsRel);
                    String folder = "report_" + taskId + "/";
                    if (pdf != null && Files.isRegularFile(pdf)) {
                        zos.putNextEntry(new ZipEntry(folder + "report_" + taskId + ".pdf"));
                        Files.copy(pdf, zos);
                        zos.closeEntry();
                        added++;
                    }
                    if (xls != null && Files.isRegularFile(xls)) {
                        zos.putNextEntry(new ZipEntry(folder + "report_" + taskId + ".xlsx"));
                        Files.copy(xls, zos);
                        zos.closeEntry();
                        added++;
                    }
                } catch (AppException e) {
                    throw e;
                } catch (Exception e) {
                    throw new AppException("E2002", "导出报告失败 #" + id + ": " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
            if (added == 0) {
                throw new AppException("NOT_FOUND", "未生成可下载的报告文件", HttpStatus.NOT_FOUND);
            }
        }
    }

    /** 生成报告时补齐溯源字段 */
    public void enrichFromTask(TestReport report, TestTask task) {
        if (task == null) return;
        if (report.getOperatorLabel() == null && task.getCreatedBy() != null) {
            userRepository.findById(task.getCreatedBy()).ifPresent(u -> {
                String label = u.getDisplayName() != null && !u.getDisplayName().isBlank()
                        ? u.getDisplayName() : u.getUsername();
                report.setOperatorLabel(label);
            });
        }
        List<TaskExecution> execs = executionRepository.findByTaskId(task.getId());
        if ((report.getDeviceLabel() == null || report.getDeviceLabel().isBlank()) && !execs.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (TaskExecution e : execs) {
                if (e.getDeviceId() == null) continue;
                deviceRepository.findById(e.getDeviceId()).ifPresentOrElse(
                        d -> labels.add(d.getSerialNumber() != null ? d.getSerialNumber() : ("#" + d.getId())),
                        () -> labels.add("#" + e.getDeviceId()));
            }
            if (!labels.isEmpty()) {
                report.setDeviceLabel(String.join(", ", labels.stream().distinct().toList()));
            }
        }
        List<RecordingResource> recs = recordingRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());
        if (!recs.isEmpty()) {
            RecordingResource rec = recs.get(0);
            if (blank(report.getModuleName()) && !blank(rec.getModuleName())) {
                report.setModuleName(rec.getModuleName());
            }
            if (blank(report.getVersionLabel()) && !blank(rec.getVersionLabel())) {
                report.setVersionLabel(rec.getVersionLabel());
            }
            if (blank(report.getProjectCode()) && !blank(rec.getProjectCode())) {
                report.setProjectCode(rec.getProjectCode());
            }
        }
        if (blank(report.getProjectCode()) && !blank(task.getAppPackage())) {
            report.setProjectCode(task.getAppPackage());
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
        return wallboard("today", null, null);
    }

    public Map<String, Object> wallboard(String timeRange, String projectCode, Long teamId) {
        Map<String, Object> row = new LinkedHashMap<>();
        long online = deviceRepository.countByStatus(Device.DeviceStatus.online);
        long busy = deviceRepository.countByStatus(Device.DeviceStatus.busy);
        long offline = deviceRepository.countByStatus(Device.DeviceStatus.offline);
        long error = deviceRepository.countByStatus(Device.DeviceStatus.error);
        long totalDevices = deviceRepository.count();

        List<TestTask> allTasks = taskRepository.findAll();
        List<TestTask> scoped = allTasks.stream()
                .filter(t -> teamId == null || Objects.equals(t.getTeamId(), teamId))
                .filter(t -> matchProject(t, projectCode))
                .toList();

        long running = scoped.stream().filter(t -> t.getStatus() == TestTask.TaskStatus.running).count();
        long queued = scoped.stream().filter(t -> t.getStatus() == TestTask.TaskStatus.queued).count();
        long pending = scoped.stream().filter(t -> t.getStatus() == TestTask.TaskStatus.pending).count();
        long success = scoped.stream().filter(t -> t.getStatus() == TestTask.TaskStatus.success).count();
        long failed = scoped.stream().filter(t -> t.getStatus() == TestTask.TaskStatus.failed).count();
        long blocked = scoped.stream().filter(t ->
                t.getStatus() == TestTask.TaskStatus.waiting_manual
                        || t.getStatus() == TestTask.TaskStatus.paused).count();
        double passRate = (success + failed) > 0 ? (double) success / (success + failed) * 100 : 0;
        double minPass = properties.getGovernance().getMinPassRate();
        long queueDepth = taskQueue.size();
        int queueThreshold = 20;

        LocalDateTime rangeStart = rangeStart(timeRange);
        List<TestReport> reports = reportRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() == null || !r.getCreatedAt().isBefore(rangeStart))
                .filter(r -> blank(projectCode) || projectCode.equalsIgnoreCase(blankToEmpty(r.getProjectCode())))
                .filter(r -> teamId == null || taskMatchesTeam(r.getTaskId(), teamId))
                .sorted(Comparator.comparing(TestReport::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        row.put("checked_at", LocalDateTime.now().toString());
        row.put("time_range", timeRange != null ? timeRange : "today");
        row.put("project_code", projectCode);
        row.put("team_id", teamId);
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
                "failed", failed,
                "blocked", blocked
        ));
        row.put("queue_depth", queueDepth);
        row.put("queue_threshold", queueThreshold);
        row.put("pass_rate", Math.round(passRate * 10) / 10.0);
        row.put("min_pass_rate", minPass);
        row.put("pass_rate_ok", passRate >= minPass);

        // 告警
        List<Map<String, Object>> alerts = new ArrayList<>();
        if (offline > 0 || error > 0) {
            alerts.add(Map.of(
                    "type", "device",
                    "target", "card-device",
                    "message", String.format("存在 %d 台离线、%d 台异常设备", offline, error)
            ));
        }
        if (queueDepth > queueThreshold || pending > queueThreshold) {
            alerts.add(Map.of(
                    "type", "queue",
                    "target", "card-queue",
                    "message", String.format("队列待提交任务堆积 %d 条（阈值 %d）", Math.max(queueDepth, pending), queueThreshold)
            ));
        }
        if (passRate < minPass && (success + failed) > 0) {
            alerts.add(Map.of(
                    "type", "pass_rate",
                    "target", "card-pass",
                    "message", String.format("全局通过率 %.1f%% 低于 %.0f%% 治理标准", passRate, minPass)
            ));
        }
        row.put("alerts", alerts);
        row.put("alert_message", alerts.isEmpty() ? "" : buildAlertBanner(offline, error, Math.max(queueDepth, pending), passRate, minPass));

        // 活跃任务明细
        List<TestTask.TaskStatus> activeStatuses = List.of(
                TestTask.TaskStatus.running,
                TestTask.TaskStatus.queued,
                TestTask.TaskStatus.waiting_manual,
                TestTask.TaskStatus.paused
        );
        List<Map<String, Object>> active = new ArrayList<>();
        for (TestTask t : scoped.stream()
                .filter(t -> activeStatuses.contains(t.getStatus()))
                .sorted(Comparator.comparing(TestTask::getStartedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .toList()) {
            active.add(buildActiveTaskRow(t));
        }
        row.put("running_tasks", active);

        // 图表
        row.put("pass_rate_trend", buildPassRateTrend(reports));
        row.put("device_load_trend", buildDeviceLoadTrend(scoped, rangeStart, online, busy, offline));
        row.put("daily_volume", buildDailyVolume(reports));
        row.put("fail_trend", buildFailTrend(reports));
        row.put("projects", reportRepository.findAll().stream()
                .map(TestReport::getProjectCode)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .sorted()
                .toList());
        return row;
    }

    private Map<String, Object> buildActiveTaskRow(TestTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName() != null ? t.getName() : "");
        m.put("platform", t.getPlatform() != null ? t.getPlatform().name() : "");
        m.put("started_at", t.getStartedAt() != null ? t.getStartedAt().toString() : "");
        m.put("status", t.getStatus() != null ? t.getStatus().name() : "");
        String statusLabel = switch (t.getStatus() != null ? t.getStatus() : TestTask.TaskStatus.pending) {
            case running -> "运行中";
            case queued -> "排队等待";
            case waiting_manual, paused -> "阻塞异常";
            default -> t.getStatus() != null ? t.getStatus().name() : "-";
        };
        m.put("status_label", statusLabel);
        String deviceLabel = "-";
        if (t.getDeviceIds() != null && t.getDeviceIds().length() > 2) {
            Matcher mId = Pattern.compile("\\d+").matcher(t.getDeviceIds());
            if (mId.find()) deviceLabel = mId.group();
        }
        List<TaskExecution> execs = executionRepository.findByTaskId(t.getId());
        if ("-".equals(deviceLabel) && !execs.isEmpty() && execs.get(0).getDeviceId() != null) {
            deviceLabel = String.valueOf(execs.get(0).getDeviceId());
        }
        m.put("device_id", deviceLabel);
        long durationSec = 0;
        if (t.getStartedAt() != null) {
            durationSec = ChronoUnit.SECONDS.between(t.getStartedAt(), LocalDateTime.now());
        }
        m.put("duration_seconds", Math.max(0, durationSec));
        m.put("duration_label", formatDuration(durationSec));
        String step = "执行中";
        for (TaskExecution e : execs) {
            if (e.getFailedStepIndex() != null) {
                step = "步骤 #" + (e.getFailedStepIndex() + 1);
                break;
            }
            if (e.getResultSummary() != null && !e.getResultSummary().isBlank()) {
                step = e.getResultSummary().length() > 40
                        ? e.getResultSummary().substring(0, 40) + "…"
                        : e.getResultSummary();
            }
        }
        if (t.getStatus() == TestTask.TaskStatus.queued) step = "等待调度";
        if (t.getStatus() == TestTask.TaskStatus.waiting_manual) step = "等待人工介入";
        if (t.getStatus() == TestTask.TaskStatus.paused) step = "已暂停";
        m.put("current_step", step);
        String version = "-";
        var reportOpt = reportRepository.findByTaskId(t.getId());
        if (reportOpt.isPresent() && !blank(reportOpt.get().getVersionLabel())) {
            version = reportOpt.get().getVersionLabel();
        }
        m.put("version_label", version);
        return m;
    }

    private List<Map<String, Object>> buildPassRateTrend(List<TestReport> reports) {
        List<Map<String, Object>> trend = new ArrayList<>();
        List<TestReport> slice = reports.size() > 30 ? reports.subList(reports.size() - 30, reports.size()) : reports;
        for (TestReport r : slice) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("task_id", r.getTaskId());
            p.put("pass_rate", r.getPassRate() != null ? r.getPassRate() : 0);
            p.put("success_count", r.getSuccessCount() != null ? r.getSuccessCount() : 0);
            p.put("failed_count", r.getFailedCount() != null ? r.getFailedCount() : 0);
            p.put("title", r.getTitle() != null ? r.getTitle() : "");
            p.put("created_at", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            trend.add(p);
        }
        return trend;
    }

    private List<Map<String, Object>> buildDeviceLoadTrend(List<TestTask> scoped, LocalDateTime rangeStart,
                                                           long online, long busy, long offline) {
        List<Map<String, Object>> points = new ArrayList<>();
        LocalDateTime cursor = rangeStart;
        LocalDateTime now = LocalDateTime.now();
        long hours = Math.max(1, ChronoUnit.HOURS.between(rangeStart, now));
        long stepHours = hours > 48 ? 6 : (hours > 12 ? 2 : 1);
        while (!cursor.isAfter(now)) {
            final LocalDateTime bucketStart = cursor;
            final LocalDateTime bucketEnd = cursor.plusHours(stepHours);
            long busyAt = scoped.stream().filter(t -> {
                if (t.getStartedAt() == null) return false;
                LocalDateTime end = t.getFinishedAt() != null ? t.getFinishedAt() : now;
                return !t.getStartedAt().isAfter(bucketEnd) && !end.isBefore(bucketStart);
            }).count();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", bucketStart.toString());
            String label = bucketStart.toLocalTime().toString();
            p.put("label", label.length() >= 5 ? label.substring(0, 5) : label);
            p.put("busy", busyAt);
            p.put("online", Math.max(online, busyAt));
            p.put("offline", offline);
            points.add(p);
            cursor = bucketEnd;
            if (points.size() > 48) break;
        }
        if (points.isEmpty()) {
            points.add(Map.of(
                    "time", now.toString(),
                    "label", "now",
                    "busy", busy,
                    "online", online,
                    "offline", offline
            ));
        }
        return points;
    }

    private List<Map<String, Object>> buildDailyVolume(List<TestReport> reports) {
        Map<String, long[]> byDay = new LinkedHashMap<>();
        for (TestReport r : reports) {
            if (r.getCreatedAt() == null) continue;
            String day = r.getCreatedAt().toLocalDate().toString();
            long[] v = byDay.computeIfAbsent(day, k -> new long[2]);
            v[0] += r.getTotalExecutions() != null ? r.getTotalExecutions() : 0;
            v[1] += r.getFailedCount() != null ? r.getFailedCount() : 0;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (var e : byDay.entrySet()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", e.getKey());
            p.put("total", e.getValue()[0]);
            p.put("failed", e.getValue()[1]);
            list.add(p);
        }
        return list;
    }

    private List<Map<String, Object>> buildFailTrend(List<TestReport> reports) {
        return buildDailyVolume(reports).stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", p.get("date"));
            m.put("failed", p.get("failed"));
            return m;
        }).toList();
    }

    private String buildAlertBanner(long offline, long error, long pending, double passRate, double minPass) {
        List<String> parts = new ArrayList<>();
        if (offline > 0 || error > 0) {
            parts.add(String.format("当前存在 %d 台离线设备", offline + error));
        }
        if (pending > 20) {
            parts.add(String.format("队列待提交任务堆积 %d 条", pending));
        }
        if (passRate < minPass) {
            parts.add(String.format("全局通过率 %.0f%% 低于 %.0f%% 治理标准", passRate, minPass));
        }
        return "【告警】" + String.join("，", parts);
    }

    private LocalDateTime rangeStart(String timeRange) {
        LocalDateTime now = LocalDateTime.now();
        if ("1h".equals(timeRange)) return now.minusHours(1);
        if ("7d".equals(timeRange)) return now.minusDays(6).toLocalDate().atStartOfDay();
        return LocalDate.now().atStartOfDay();
    }

    private boolean matchProject(TestTask t, String projectCode) {
        if (blank(projectCode)) return true;
        if (projectCode.equalsIgnoreCase(blankToEmpty(t.getAppPackage()))) return true;
        return reportRepository.findByTaskId(t.getId())
                .map(r -> projectCode.equalsIgnoreCase(blankToEmpty(r.getProjectCode())))
                .orElse(false);
    }

    private boolean taskMatchesTeam(Long taskId, Long teamId) {
        if (taskId == null || teamId == null) return true;
        return taskRepository.findById(taskId).map(t -> Objects.equals(t.getTeamId(), teamId)).orElse(false);
    }

    private static String formatDuration(long sec) {
        if (sec < 60) return sec + "s";
        long m = sec / 60;
        long s = sec % 60;
        if (m < 60) return s > 0 ? m + "分" + s + "秒" : m + "分钟";
        long h = m / 60;
        long rm = m % 60;
        return h + "小时" + (rm > 0 ? rm + "分" : "");
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private TestReport get(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "报告不存在", HttpStatus.NOT_FOUND));
    }

    private int retainDays() {
        int d = properties.getStorage() != null ? properties.getStorage().getRetainDays() : DEFAULT_RETAIN_DAYS;
        return d > 0 ? d : DEFAULT_RETAIN_DAYS;
    }

    private long ageDays(TestReport r) {
        if (r.getCreatedAt() == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(r.getCreatedAt().toLocalDate(), LocalDate.now()));
    }

    private String resolveDisplayStatus(TestReport r) {
        if (Boolean.TRUE.equals(r.getArchived())) return "archived";
        if (ageDays(r) >= retainDays()) return "expired";
        return "active";
    }

    private String displayStatusLabel(String code) {
        return switch (code) {
            case "archived" -> "已归档";
            case "expired" -> "已过期";
            default -> "未归档";
        };
    }

    private Specification<TestReport> buildSpec(Map<String, Object> filters) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (filters == null) {
                query.orderBy(cb.desc(root.get("createdAt")));
                return cb.and(preds.toArray(new Predicate[0]));
            }
            String keyword = asStr(filters.get("keyword"));
            String moduleName = asStr(filters.get("module_name"));
            String versionLabel = asStr(filters.get("version_label"));
            Long taskId = asLong(filters.get("task_id"));
            String passBucket = asStr(filters.get("pass_bucket"));
            String status = asStr(filters.get("status"));
            String createdRange = asStr(filters.get("created_range"));
            LocalDateTime createdFrom = asDateTime(filters.get("created_from"));
            LocalDateTime createdTo = asDateTime(filters.get("created_to"));

            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                preds.add(cb.like(cb.lower(cb.coalesce(root.get("title"), cb.literal(""))), like));
            }
            if (moduleName != null) preds.add(cb.equal(root.get("moduleName"), moduleName));
            if (versionLabel != null) preds.add(cb.equal(root.get("versionLabel"), versionLabel));
            if (taskId != null) preds.add(cb.equal(root.get("taskId"), taskId));
            if (passBucket != null) {
                switch (passBucket) {
                    case "low" -> preds.add(cb.lt(root.get("passRate"), 60.0));
                    case "mid" -> preds.add(cb.and(
                            cb.ge(root.get("passRate"), 60.0),
                            cb.lt(root.get("passRate"), 90.0)));
                    case "high" -> preds.add(cb.ge(root.get("passRate"), 90.0));
                    default -> { }
                }
            }
            if (createdFrom != null) preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            if (createdTo != null) preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            if (createdRange != null && createdFrom == null && createdTo == null) {
                LocalDate today = LocalDate.now();
                switch (createdRange) {
                    case "today" -> {
                        preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), today.atStartOfDay()));
                        preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), today.atTime(LocalTime.MAX)));
                    }
                    case "7d" -> preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                            today.minusDays(6).atStartOfDay()));
                    case "30d" -> preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                            today.minusDays(29).atStartOfDay()));
                    default -> { }
                }
            }
            if (status != null) {
                int retain = retainDays();
                LocalDateTime expireBefore = LocalDate.now().minusDays(retain).atStartOfDay();
                switch (status) {
                    case "archived" -> preds.add(cb.isTrue(root.get("archived")));
                    case "active", "unarchived" -> {
                        preds.add(cb.or(cb.isNull(root.get("archived")), cb.isFalse(root.get("archived"))));
                        preds.add(cb.greaterThan(root.get("createdAt"), expireBefore));
                    }
                    case "expired" -> {
                        preds.add(cb.or(cb.isNull(root.get("archived")), cb.isFalse(root.get("archived"))));
                        preds.add(cb.lessThanOrEqualTo(root.get("createdAt"), expireBefore));
                    }
                    default -> { }
                }
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private Path resolveReportFile(Path reportsRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        String rel = relativePath.startsWith("reports/")
                ? relativePath.substring("reports/".length()) : relativePath;
        Path file = reportsRoot.resolve(rel).normalize();
        if (!file.startsWith(reportsRoot)) return null;
        return file;
    }

    private void deleteReportFiles(TestReport report) {
        if (report.getTaskId() == null) return;
        try {
            Path dir = Path.of(properties.getStorage().getReportsPath(), report.getTaskId().toString());
            if (Files.isDirectory(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                    });
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String asStr(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long asLong(Object v) {
        if (v == null || "".equals(v)) return null;
        try {
            return Long.valueOf(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime asDateTime(Object v) {
        if (v == null || "".equals(v)) return null;
        if (v instanceof LocalDateTime ldt) return ldt;
        try {
            String s = v.toString().trim();
            if (s.length() <= 10) return LocalDate.parse(s).atStartOfDay();
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
