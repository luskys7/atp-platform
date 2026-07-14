package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.PlatformMessage;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.PlatformMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class PlatformMessageController {

    private final PlatformMessageService service;

    @GetMapping
    public ApiResponse<List<PlatformMessage>> list() {
        return ApiResponse.ok(service.listForUser(SecurityUtils.currentUserId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("count", service.unreadCount(SecurityUtils.currentUserId())));
    }

    @PostMapping("/mark-read")
    public ApiResponse<Map<String, Object>> markRead(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = body.get("ids") != null
                ? ((List<Number>) body.get("ids")).stream().map(Number::longValue).toList()
                : List.of();
        return ApiResponse.ok(service.markRead(SecurityUtils.currentUserId(), ids));
    }

    @PostMapping("/mark-all-read")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.ok(Map.of("marked", service.markAllRead(SecurityUtils.currentUserId())));
    }
}
