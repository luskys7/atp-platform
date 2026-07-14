package com.atp.platform.service;

import com.atp.platform.entity.TestTask;
import com.atp.platform.repository.TestTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QueueBoardService {

    private final TestTaskRepository taskRepository;
    private final TaskQueueService taskQueue;

    public Map<String, Object> snapshot() {
        List<TestTask.TaskStatus> statuses = List.of(
                TestTask.TaskStatus.pending,
                TestTask.TaskStatus.queued,
                TestTask.TaskStatus.running,
                TestTask.TaskStatus.paused
        );
        List<TestTask> tasks = taskRepository.findByStatusInOrderByPriorityAscCreatedAtAsc(statuses);

        List<Map<String, Object>> items = new ArrayList<>();
        int position = 1;
        for (TestTask t : tasks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("position", position++);
            item.put("task_id", t.getId());
            item.put("name", t.getName());
            item.put("status", t.getStatus().name());
            item.put("priority", t.getPriority());
            item.put("platform", t.getPlatform() != null ? t.getPlatform().name() : "");
            item.put("parallel_count", t.getParallelCount());
            item.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().toString() : "");
            item.put("started_at", t.getStartedAt() != null ? t.getStartedAt().toString() : "");
            if (t.getCreatedAt() != null && t.getStatus() == TestTask.TaskStatus.queued) {
                item.put("wait_seconds", Duration.between(t.getCreatedAt(), LocalDateTime.now()).getSeconds());
            } else {
                item.put("wait_seconds", 0);
            }
            items.add(item);
        }

        long queued = taskRepository.countByStatus(TestTask.TaskStatus.queued);
        long pending = taskRepository.countByStatus(TestTask.TaskStatus.pending);
        long running = taskRepository.countByStatus(TestTask.TaskStatus.running);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queue_depth", taskQueue.size());
        result.put("queued_tasks", queued);
        result.put("pending_tasks", pending);
        result.put("running_tasks", running);
        result.put("items", items);
        result.put("checked_at", LocalDateTime.now().toString());
        return result;
    }
}
