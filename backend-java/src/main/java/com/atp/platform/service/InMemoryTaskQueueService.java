package com.atp.platform.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("local")
public class InMemoryTaskQueueService implements TaskQueueService {

    private final PriorityBlockingQueue<QueueEntry> queue = new PriorityBlockingQueue<>(
            11, Comparator.comparingInt(QueueEntry::priority));

    @Override
    public long size() {
        return queue.size();
    }

    @Override
    public void enqueue(String item) {
        int priority = 5;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(item);
            if (node.has("priority")) {
                priority = node.get("priority").asInt();
            }
        } catch (Exception ignored) {
        }
        queue.offer(new QueueEntry(priority, item));
    }

    @Override
    public String dequeue() {
        QueueEntry entry = queue.poll();
        return entry != null ? entry.payload() : null;
    }

    private record QueueEntry(int priority, String payload) {}
}
