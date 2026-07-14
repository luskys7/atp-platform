package com.atp.platform.service;

import com.atp.platform.entity.ManualIntervention;
import com.atp.platform.entity.PlatformMessage;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.ManualInterventionRepository;
import com.atp.platform.repository.TestTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ManualInterventionService {

    private final ManualInterventionRepository repository;
    private final TestTaskRepository taskRepository;
    private final PlatformMessageService messageService;
    private final CheckpointService checkpointService;
    private final TaskService taskService;

    public List<ManualIntervention> listPending() {
        return repository.findByStatusOrderByCreatedAtDesc(ManualIntervention.InterventionStatus.pending);
    }

    public List<ManualIntervention> listByTask(Long taskId) {
        return repository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    @Transactional
    public ManualIntervention create(Long taskId, Long executionId, int stepIndex, String prompt) {
        ManualIntervention iv = new ManualIntervention();
        iv.setTaskId(taskId);
        iv.setExecutionId(executionId);
        iv.setStepIndex(stepIndex);
        iv.setPrompt(prompt != null ? prompt : "等待人工处理");
        iv.setStatus(ManualIntervention.InterventionStatus.pending);
        ManualIntervention saved = repository.save(iv);

        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(TestTask.TaskStatus.waiting_manual);
            taskRepository.save(task);
            if (task.getCreatedBy() != null) {
                messageService.send(task.getCreatedBy(), "人工介入待处理",
                        "任务「" + task.getName() + "」步骤 #" + stepIndex + " 需要人工处理：" + saved.getPrompt(),
                        "manual_intervention", taskId, saved.getId());
            }
        });
        return saved;
    }

    @Transactional
    public Map<String, Object> resolve(Long id, String action, Long operatorId, String note) {
        ManualIntervention iv = repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "介入记录不存在", HttpStatus.NOT_FOUND));
        if (iv.getStatus() != ManualIntervention.InterventionStatus.pending) {
            throw new AppException("INVALID", "该介入已处理", HttpStatus.BAD_REQUEST);
        }
        boolean skip = "skip".equalsIgnoreCase(action);
        iv.setStatus(skip ? ManualIntervention.InterventionStatus.skipped : ManualIntervention.InterventionStatus.resolved);
        iv.setResolvedBy(operatorId);
        iv.setResolutionNote(note);
        iv.setResolvedAt(LocalDateTime.now());
        repository.save(iv);

        int fromStep = iv.getStepIndex() != null ? iv.getStepIndex() : 1;
        if (skip) fromStep = fromStep + 1;

        TestTask resumed = checkpointService.resumeTask(iv.getTaskId(), fromStep, operatorId);
        taskService.saveLog(iv.getTaskId(), null, null, "platform", "info",
                "人工介入已" + (skip ? "跳过" : "确认") + "，从步骤 " + fromStep + " 续跑");
        return Map.of("intervention_id", id, "task_id", resumed.getId(), "resume_from_step", fromStep);
    }

    @Transactional
    public void cancel(Long id, Long operatorId) {
        ManualIntervention iv = repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "介入记录不存在", HttpStatus.NOT_FOUND));
        iv.setStatus(ManualIntervention.InterventionStatus.cancelled);
        iv.setResolvedBy(operatorId);
        iv.setResolvedAt(LocalDateTime.now());
        repository.save(iv);
        taskRepository.findById(iv.getTaskId()).ifPresent(t -> {
            t.setStatus(TestTask.TaskStatus.cancelled);
            t.setFinishedAt(LocalDateTime.now());
            taskRepository.save(t);
        });
    }
}
