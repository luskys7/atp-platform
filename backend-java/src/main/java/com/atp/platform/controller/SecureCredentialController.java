package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.SecureCredential;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.SecureCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/credentials")
@RequiredArgsConstructor
public class SecureCredentialController {

    private final SecureCredentialService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<SecureCredential> create(@RequestBody Map<String, Object> body) {
        SecureCredential c = service.create(body, SecurityUtils.currentUserId());
        c.setValueCipher(null);
        return ApiResponse.ok(c);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<SecureCredential> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SecureCredential c = service.update(id, body);
        c.setValueCipher(null);
        return ApiResponse.ok(c);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
