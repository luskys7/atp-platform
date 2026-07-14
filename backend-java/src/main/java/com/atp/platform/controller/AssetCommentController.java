package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.AssetComment;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.AssetCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/asset-comments")
@RequiredArgsConstructor
public class AssetCommentController {

    private final AssetCommentService service;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam("asset_type") String assetType,
            @RequestParam("asset_id") Long assetId) {
        return ApiResponse.ok(service.list(assetType, assetId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<AssetComment> create(@RequestBody Map<String, Object> body) {
        String assetType = body.get("asset_type").toString();
        Long assetId = Long.valueOf(body.get("asset_id").toString());
        String content = body.get("content").toString();
        return ApiResponse.ok(service.create(assetType, assetId, content, SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        boolean isAdmin = SecurityUtils.hasAnyRole("super_admin", "test_admin");
        service.delete(id, SecurityUtils.currentUserId(), isAdmin);
        return ApiResponse.ok();
    }
}
