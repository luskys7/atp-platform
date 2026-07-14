package com.atp.platform.service;

import com.atp.platform.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@Profile("!local")
@RequiredArgsConstructor
public class RedisTaskQueueService implements TaskQueueService {

    private static final String PRIORITY_QUEUE_KEY = AppConfig.TASK_QUEUE_KEY + ":priority";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public long size() {
        Long s = redis.opsForZSet().size(PRIORITY_QUEUE_KEY);
        return s != null ? s : 0;
    }

    @Override
    public void enqueue(String item) {
        double score = resolveScore(item);
        redis.opsForZSet().add(PRIORITY_QUEUE_KEY, item, score);
    }

    @Override
    public String dequeue() {
        Set<String> items = redis.opsForZSet().range(PRIORITY_QUEUE_KEY, 0, 0);
        if (items == null || items.isEmpty()) {
            return null;
        }
        String item = items.iterator().next();
        redis.opsForZSet().remove(PRIORITY_QUEUE_KEY, item);
        return item;
    }

    /** 分数越小优先级越高；同优先级按时间 FIFO */
    private double resolveScore(String item) {
        int priority = 5;
        try {
            JsonNode node = objectMapper.readTree(item);
            if (node.has("priority")) {
                priority = node.get("priority").asInt();
            }
        } catch (Exception ignored) {
        }
        return priority * 1_000_000_000_000L + System.nanoTime() % 1_000_000_000_000L;
    }
}
