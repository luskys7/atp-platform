package com.atp.platform.service;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.entity.ExecutionLog;
import com.atp.platform.util.LogDesensitizer;
import com.atp.platform.entity.TaskExecution;
import com.atp.platform.entity.TestReport;
import com.atp.platform.entity.TestTask;
import com.atp.platform.repository.ExecutionLogRepository;
import com.atp.platform.repository.TaskExecutionRepository;
import com.atp.platform.repository.TestTaskRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelReportService {

    private final TestTaskRepository taskRepository;
    private final TaskExecutionRepository executionRepository;
    private final ExecutionLogRepository logRepository;
    private final AtpProperties properties;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String generate(TestReport report) throws Exception {
        TestTask task = taskRepository.findById(report.getTaskId()).orElseThrow();
        List<TaskExecution> executions = executionRepository.findByTaskId(report.getTaskId());
        List<ExecutionLog> logs = logRepository.findByTaskIdOrderByCreatedAtAsc(report.getTaskId());

        Path dir = Path.of(properties.getStorage().getReportsPath(), report.getTaskId().toString());
        Files.createDirectories(dir);
        Path xlsxPath = dir.resolve("report_" + report.getId() + ".xlsx");

        try (Workbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(xlsxPath)) {
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Sheet summary = wb.createSheet("摘要");
            int r = 0;
            r = row(summary, r, "任务名称", task.getName());
            r = row(summary, r, "任务 ID", String.valueOf(task.getId()));
            r = row(summary, r, "平台", task.getPlatform() != null ? task.getPlatform().name() : "");
            r = row(summary, r, "总执行次数", String.valueOf(report.getTotalExecutions()));
            r = row(summary, r, "成功", String.valueOf(report.getSuccessCount()));
            r = row(summary, r, "失败", String.valueOf(report.getFailedCount()));
            r = row(summary, r, "通过率", String.format("%.2f%%", report.getPassRate() != null ? report.getPassRate() : 0));
            row(summary, r, "摘要", report.getSummary() != null ? report.getSummary() : "");
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            Sheet execSheet = wb.createSheet("执行实例");
            Row execHeader = execSheet.createRow(0);
            String[] execCols = {"执行ID", "设备ID", "状态", "开始时间", "结束时间", "错误信息"};
            for (int i = 0; i < execCols.length; i++) {
                Cell c = execHeader.createCell(i);
                c.setCellValue(execCols[i]);
                c.setCellStyle(headerStyle);
            }
            int er = 1;
            for (TaskExecution exec : executions) {
                Row row = execSheet.createRow(er++);
                row.createCell(0).setCellValue(exec.getId());
                row.createCell(1).setCellValue(exec.getDeviceId() != null ? exec.getDeviceId() : 0);
                row.createCell(2).setCellValue(exec.getStatus() != null ? exec.getStatus().name() : "");
                row.createCell(3).setCellValue(exec.getStartedAt() != null ? exec.getStartedAt().format(FMT) : "");
                row.createCell(4).setCellValue(exec.getFinishedAt() != null ? exec.getFinishedAt().format(FMT) : "");
                row.createCell(5).setCellValue(exec.getErrorMessage() != null ? exec.getErrorMessage() : "");
            }
            for (int i = 0; i < execCols.length; i++) {
                execSheet.autoSizeColumn(i);
            }

            Sheet logSheet = wb.createSheet("执行日志");
            Row logHeader = logSheet.createRow(0);
            String[] logCols = {"时间", "类型", "级别", "消息"};
            for (int i = 0; i < logCols.length; i++) {
                Cell c = logHeader.createCell(i);
                c.setCellValue(logCols[i]);
                c.setCellStyle(headerStyle);
            }
            int lr = 1;
            int logLimit = Math.min(logs.size(), 500);
            for (int i = 0; i < logLimit; i++) {
                ExecutionLog log = logs.get(i);
                Row row = logSheet.createRow(lr++);
                row.createCell(0).setCellValue(log.getCreatedAt() != null ? log.getCreatedAt().format(FMT) : "");
                row.createCell(1).setCellValue(log.getLogType() != null ? log.getLogType() : "");
                row.createCell(2).setCellValue(log.getLevel() != null ? log.getLevel() : "");
                row.createCell(3).setCellValue(LogDesensitizer.mask(log.getMessage() != null ? log.getMessage() : ""));
            }
            for (int i = 0; i < logCols.length; i++) {
                logSheet.autoSizeColumn(i);
            }

            wb.write(out);
        }
        return "reports/" + report.getTaskId() + "/report_" + report.getId() + ".xlsx";
    }

    private int row(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIdx + 1;
    }
}
