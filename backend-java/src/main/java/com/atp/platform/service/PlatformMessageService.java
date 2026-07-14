package com.atp.platform.service;

import com.atp.platform.entity.PlatformMessage;
import com.atp.platform.repository.PlatformMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlatformMessageService {

    private final PlatformMessageRepository repository;
    private static final int CONVERGE_MINUTES = 10;
    private static final int PLATFORM_ALERT_MINUTES = 30;

    public List<PlatformMessage> listForUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public PlatformMessage send(Long userId, String title, String content,
                                String messageType, Long relatedTaskId, Long relatedRefId) {
        if (shouldConverge(userId, messageType, relatedTaskId, title)) {
            return repository
                    .findTopByUserIdAndMessageTypeAndRelatedTaskIdOrderByCreatedAtDesc(
                            userId, messageType, relatedTaskId)
                    .orElseGet(() -> create(userId, title, content, messageType, relatedTaskId, relatedRefId));
        }
        return create(userId, title, content, messageType, relatedTaskId, relatedRefId);
    }

    /** 平台级健康告警（无 task_id，按 title 收敛） */
    @Transactional
    public PlatformMessage sendPlatformAlert(Long userId, String title, String content) {
        String type = "platform_health";
        Optional<PlatformMessage> recent = repository.findTopByUserIdAndMessageTypeOrderByCreatedAtDesc(userId, type);
        if (recent.isPresent()
                && recent.get().getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(PLATFORM_ALERT_MINUTES))
                && title != null && title.equals(recent.get().getTitle())) {
            return recent.get();
        }
        return create(userId, title, content, type, null, null);
    }

    private boolean shouldConverge(Long userId, String messageType, Long relatedTaskId, String title) {
        if (userId == null || relatedTaskId == null || messageType == null) return false;
        Optional<PlatformMessage> recent = repository
                .findTopByUserIdAndMessageTypeAndRelatedTaskIdOrderByCreatedAtDesc(
                        userId, messageType, relatedTaskId);
        return recent.isPresent()
                && recent.get().getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(CONVERGE_MINUTES))
                && title != null && title.equals(recent.get().getTitle());
    }

    private PlatformMessage create(Long userId, String title, String content,
                                   String messageType, Long relatedTaskId, Long relatedRefId) {
        PlatformMessage msg = new PlatformMessage();
        msg.setUserId(userId);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setMessageType(messageType != null ? messageType : "info");
        msg.setRelatedTaskId(relatedTaskId);
        msg.setRelatedRefId(relatedRefId);
        return repository.save(msg);
    }

    @Transactional
    public Map<String, Object> markRead(Long userId, List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            repository.findById(id).ifPresent(m -> {
                if (m.getUserId().equals(userId) && m.getReadAt() == null) {
                    m.setReadAt(LocalDateTime.now());
                    repository.save(m);
                }
            });
            count++;
        }
        return Map.of("marked", count);
    }

    @Transactional
    public int markAllRead(Long userId) {
        int n = 0;
        for (PlatformMessage m : repository.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (m.getReadAt() == null) {
                m.setReadAt(LocalDateTime.now());
                repository.save(m);
                n++;
            }
        }
        return n;
    }
}
