package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.TestAccount;
import com.atp.platform.service.TestAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class TestAccountController {

    private final TestAccountService service;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<TestAccount> get(@PathVariable Long id) {
        TestAccount a = service.get(id);
        a.setPasswordCipher(null);
        return ApiResponse.ok(a);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<TestAccount> create(@RequestBody Map<String, Object> body) {
        TestAccount a = service.create(body);
        a.setPasswordCipher(null);
        return ApiResponse.ok(a);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<TestAccount> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        TestAccount a = service.update(id, body);
        a.setPasswordCipher(null);
        return ApiResponse.ok(a);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/acquire")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> acquire(@RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("task_id").toString());
        Long envId = body.get("env_id") != null ? Long.valueOf(body.get("env_id").toString()) : null;
        String tags = body.get("tags") != null ? body.get("tags").toString() : null;
        TestAccount a = service.acquireForTask(taskId, envId, tags);
        return ApiResponse.ok(Map.of(
                "account_id", a.getId(),
                "username", a.getUsername(),
                "message", "账号已占用"
        ));
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> release(@PathVariable Long id) {
        service.release(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        service.archive(id);
        return ApiResponse.ok();
    }
}
