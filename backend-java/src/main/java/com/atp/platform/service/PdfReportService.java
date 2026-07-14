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
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfReportService {

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
        Path pdfPath = dir.resolve("report_" + report.getId() + ".pdf");

        try (OutputStream out = Files.newOutputStream(pdfPath)) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            doc.add(new Paragraph("TestFlow 测试报告", titleFont));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("任务名称: " + task.getName(), bodyFont));
            doc.add(new Paragraph("任务 ID: " + task.getId(), bodyFont));
            doc.add(new Paragraph("平台: " + task.getPlatform(), bodyFont));
            doc.add(new Paragraph("总执行次数: " + report.getTotalExecutions(), bodyFont));
            doc.add(new Paragraph("成功: " + report.getSuccessCount() + "  失败: " + report.getFailedCount(), bodyFont));
            doc.add(new Paragraph(String.format("通过率: %.2f%%", report.getPassRate()), bodyFont));
            doc.add(new Paragraph("摘要: " + (report.getSummary() != null ? report.getSummary() : ""), bodyFont));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("执行实例", headFont));
            for (TaskExecution exec : executions) {
                String line = String.format("#%d  设备=%d  状态=%s  开始=%s  结束=%s",
                        exec.getId(), exec.getDeviceId(), exec.getStatus(),
                        exec.getStartedAt() != null ? exec.getStartedAt().format(FMT) : "-",
                        exec.getFinishedAt() != null ? exec.getFinishedAt().format(FMT) : "-");
                doc.add(new Paragraph(line, bodyFont));
                if (exec.getErrorMessage() != null && !exec.getErrorMessage().isBlank()) {
                    doc.add(new Paragraph("  错误: " + exec.getErrorMessage(), bodyFont));
                }
            }
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("执行日志（最近 100 条）", headFont));
            int logLimit = Math.min(logs.size(), 100);
            for (int i = 0; i < logLimit; i++) {
                ExecutionLog log = logs.get(i);
                doc.add(new Paragraph(String.format("[%s/%s] %s", log.getLogType(), log.getLevel(),
                        LogDesensitizer.mask(log.getMessage())), bodyFont));
            }

            doc.close();
        }

        return "reports/" + report.getTaskId() + "/report_" + report.getId() + ".pdf";
    }
}
