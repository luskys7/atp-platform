package com.atp.platform.repository;

import com.atp.platform.entity.PlatformMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformMessageRepository extends JpaRepository<PlatformMessage, Long> {
    List<PlatformMessage> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndReadAtIsNull(Long userId);
    Optional<PlatformMessage> findTopByUserIdAndMessageTypeAndRelatedTaskIdOrderByCreatedAtDesc(
            Long userId, String messageType, Long relatedTaskId);

    Optional<PlatformMessage> findTopByUserIdAndMessageTypeOrderByCreatedAtDesc(Long userId, String messageType);

    List<PlatformMessage> findByRelatedTaskId(Long relatedTaskId);
}
