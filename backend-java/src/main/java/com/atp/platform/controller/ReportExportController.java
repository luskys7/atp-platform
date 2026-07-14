package com.atp.platform.controller;

import com.atp.platform.config.AtpProperties;
import com.atp.platform.exception.AppException;
import com.atp.platform.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@RestController
@RequiredArgsConstructor
public class ReportExportController {

    private final ReportService reportService;
    private final AtpProperties properties;

    @GetMapping("/api/v1/reports/{taskId}/export")
    public ResponseEntity<Resource> exportPdf(@PathVariable Long taskId) {
        String relativePath = reportService.exportPdf(taskId);
        return buildFileResponse(relativePath, taskId, "pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/api/v1/reports/{taskId}/export/excel")
    public ResponseEntity<Resource> exportExcel(@PathVariable Long taskId) {
        String relativePath = reportService.exportExcel(taskId);
        return buildFileResponse(relativePath, taskId, "xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private ResponseEntity<Resource> buildFileResponse(String relativePath, Long taskId,
                                                       String ext, MediaType contentType) {
        Path file = Path.of(properties.getStorage().getReportsPath()).resolve(
                relativePath.startsWith("reports/") ? relativePath.substring("reports/".length()) : relativePath
        ).normalize();
        if (!file.toFile().exists()) {
            throw new AppException("NOT_FOUND", "报告文件不存在", HttpStatus.NOT_FOUND);
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report_" + taskId + "." + ext + "\"")
                .body(resource);
    }
}
