package com.atp.platform.repository;

import com.atp.platform.entity.RecycleBinItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecycleBinItemRepository extends JpaRepository<RecycleBinItem, Long> {
    List<RecycleBinItem> findAllByOrderByDeletedAtDesc();
}
