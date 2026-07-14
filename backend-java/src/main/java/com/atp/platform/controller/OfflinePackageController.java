package com.atp.platform.controller;

import com.atp.platform.service.OfflinePackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/offline-packages")
@RequiredArgsConstructor
public class OfflinePackageController {

    private final OfflinePackageService offlinePackageService;

    @GetMapping("/{filename}/download")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        byte[] data = offlinePackageService.readPackage(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}
