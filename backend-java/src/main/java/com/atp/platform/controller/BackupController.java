package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.AuthService;
import com.atp.platform.service.BackupService;
import com.atp.platform.service.SeedDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService service;
    private final SeedDataService seedDataService;
    private final AuthService authService;

    @GetMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> create() {
        return ApiResponse.ok(service.create(SecurityUtils.currentUserId()));
    }

    @PostMapping("/{filename}/restore")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> restore(@PathVariable String filename, HttpServletRequest request) {
        Map<String, Object> result = service.restore(filename);
        authService.createAuditLog(SecurityUtils.currentUserId(), "restore", "backup", filename,
                String.valueOf(result.get("count")), request.getRemoteAddr());
        return ApiResponse.ok(result);
    }

    @PostMapping("/install-portable-seed")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Map<String, Object>> installPortableSeed(HttpServletRequest request) {
        Map<String, Object> result = seedDataService.importPortableSeed();
        authService.createAuditLog(SecurityUtils.currentUserId(), "restore", "seed", "atp_portable_seed.zip",
                String.valueOf(result.get("count")), request.getRemoteAddr());
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{filename}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable String filename) {
        service.delete(filename);
        return ApiResponse.ok();
    }

    @GetMapping("/{filename}/download")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        byte[] data = service.download(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}
