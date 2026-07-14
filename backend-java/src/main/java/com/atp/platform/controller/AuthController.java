package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.common.PageResult;
import com.atp.platform.dto.LoginRequest;
import com.atp.platform.entity.User;
import com.atp.platform.security.JwtTokenProvider;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.AuthService;
import com.atp.platform.service.SsoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SsoService ssoService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        User user = authService.login(req.getUsername(), req.getPassword());
        user.setPasswordHash(null);
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole().name(), user.getTeamId());
        return ApiResponse.ok(Map.of(
                "token", token,
                "expires_at", jwtTokenProvider.getExpirationTime(),
                "user", user
        ));
    }

    @GetMapping("/sso/config")
    public ApiResponse<Map<String, Object>> ssoConfig() {
        return ApiResponse.ok(ssoService.config());
    }

    @PostMapping("/sso/login")
    public ApiResponse<Map<String, Object>> ssoLogin(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(ssoService.exchange(body.get("sso_token")));
    }

    @GetMapping("/profile")
    public ApiResponse<User> profile() {
        User user = authService.getUserById(SecurityUtils.currentUserId());
        user.setPasswordHash(null);
        return ApiResponse.ok(user);
    }

    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(@RequestBody Map<String, String> body) {
        authService.updateProfile(SecurityUtils.currentUserId(), body.get("display_name"));
        return ApiResponse.ok();
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body) {
        authService.changePassword(SecurityUtils.currentUserId(), body.get("old_password"), body.get("new_password"));
        return ApiResponse.ok();
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<List<Map<String, Object>>> listUsers() {
        return ApiResponse.ok(authService.listUsersBrief());
    }
}
