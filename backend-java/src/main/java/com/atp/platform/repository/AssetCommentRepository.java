package com.atp.platform.repository;

import com.atp.platform.entity.AssetComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetCommentRepository extends JpaRepository<AssetComment, Long> {
    List<AssetComment> findByAssetTypeAndAssetIdOrderByCreatedAtAsc(String assetType, Long assetId);
}
