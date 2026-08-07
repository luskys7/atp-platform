package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.service.ExecutorLauncherService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

/**
 * 访客本机执行器启动器下载（设备页「下载启动器」）。
 */
@RestController
@RequestMapping("/api/v1/downloads")
@RequiredArgsConstructor
public class DownloadsController {

    private final ExecutorLauncherService launcherService;

    @GetMapping("/executor-launcher/info")
    public ApiResponse<Map<String, Object>> launcherInfo() {
        return ApiResponse.ok(launcherService.info());
    }

    @GetMapping("/executor-launcher")
    public ResponseEntity<Resource> downloadLauncher() {
        Path file = launcherService.requireLauncherFile();
        String filename = file.getFileName().toString();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(new FileSystemResource(file));
    }
}
