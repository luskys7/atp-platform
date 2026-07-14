package com.atp.platform.service;

import com.atp.platform.entity.AssetComment;
import com.atp.platform.entity.User;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.AssetCommentRepository;
import com.atp.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetCommentService {

    private final AssetCommentRepository commentRepository;
    private final UserRepository userRepository;

    public List<Map<String, Object>> list(String assetType, Long assetId) {
        List<AssetComment> comments = commentRepository.findByAssetTypeAndAssetIdOrderByCreatedAtAsc(assetType, assetId);
        Map<Long, User> users = userRepository.findAllById(
                comments.stream().map(AssetComment::getUserId).distinct().toList()
        ).stream().collect(Collectors.toMap(User::getId, u -> u));
        return comments.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("asset_type", c.getAssetType());
            row.put("asset_id", c.getAssetId());
            row.put("user_id", c.getUserId());
            User u = users.get(c.getUserId());
            row.put("author_name", u != null ? (u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()) : "未知");
            row.put("content", c.getContent());
            row.put("created_at", c.getCreatedAt());
            row.put("updated_at", c.getUpdatedAt());
            return row;
        }).toList();
    }

    @Transactional
    public AssetComment create(String assetType, Long assetId, String content, Long userId) {
        if (content == null || content.isBlank()) {
            throw new AppException("INVALID", "批注内容不能为空", HttpStatus.BAD_REQUEST);
        }
        AssetComment c = new AssetComment();
        c.setAssetType(assetType);
        c.setAssetId(assetId);
        c.setUserId(userId);
        c.setContent(content.trim());
        return commentRepository.save(c);
    }

    @Transactional
    public void delete(Long id, Long userId, boolean isAdmin) {
        AssetComment c = commentRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "批注不存在", HttpStatus.NOT_FOUND));
        if (!isAdmin && !c.getUserId().equals(userId)) {
            throw new AppException("FORBIDDEN", "无权删除该批注", HttpStatus.FORBIDDEN);
        }
        commentRepository.delete(c);
    }
}
