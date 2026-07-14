package com.atp.platform.service;

import com.atp.platform.dto.CreateTaskRequest;
import com.atp.platform.entity.ExecutionLog;
import com.atp.platform.entity.TaskExecution;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.ExecutionLogRepository;
import com.atp.platform.repository.TaskExecutionRepository;
import com.atp.platform.repository.TestReportRepository;
import com.atp.platform.repository.TestTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {

    private final TestTaskRepository taskRepository;
    private final TaskExecutionRepository executionRepository;
    private final ExecutionLogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final VisualScriptGenerator visualScriptGenerator;
    private final TeamScopeService teamScope;
    private final AppPackageService appPackageService;
    private final EnvironmentGovernanceService environmentGovernanceService;
    private final TaskDeletionService taskDeletionService;
    private final TestReportRepository reportRepository;
    private final EntityManager entityManager;

    public TaskService(TestTaskRepository taskRepository,
                       TaskExecutionRepository executionRepository,
                       ExecutionLogRepository logRepository,
                       ObjectMapper objectMapper,
                       VisualScriptGenerator visualScriptGenerator,
                       TeamScopeService teamScope,
                       AppPackageService appPackageService,
                       EnvironmentGovernanceService environmentGovernanceService,
                       TaskDeletionService taskDeletionService,
                       TestReportRepository reportRepository,
                       EntityManager entityManager) {
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
        this.visualScriptGenerator = visualScriptGenerator;
        this.teamScope = teamScope;
        this.appPackageService = appPackageService;
        this.environmentGovernanceService = environmentGovernanceService;
        this.taskDeletionService = taskDeletionService;
        this.reportRepository = reportRepository;
        this.entityManager = entityManager;
    }

    public TestTask create(CreateTaskRequest req, Long createdBy) {
        TestTask task = new TestTask();
        task.setName(req.getName());
        task.setDescription(req.getDescription());
        task.setPlatform(TestTask.TaskPlatform.valueOf(req.getPlatform()));
        task.setScriptType(req.getScriptType());
        task.setScriptContent(req.getScriptContent());
        task.setAppPackage(req.getAppPackage());
        task.setAppPath(req.getAppPath());
        task.setPriority(req.getPriority() != null ? req.getPriority() : (byte) 5);
        task.setParallelCount(req.getParallelCount() != null ? req.getParallelCount() : 1);
        task.setTimeoutSeconds(req.getTimeoutSeconds() != null ? req.getTimeoutSeconds() : 3600);
        task.setMaxRetries(req.getMaxRetries() != null ? req.getMaxRetries() : 0);
        task.setEnableRecording(req.getEnableRecording() != null ? req.getEnableRecording() : true);
        task.setUseAccountPool(Boolean.TRUE.equals(req.getUseAccountPool()));
        if (req.getEnvId() != null) task.setEnvId(req.getEnvId());
        task.setCreatedBy(createdBy);
        task.setTeamId(teamScope.teamIdForCreate(null));
        task.setStatus(TestTask.TaskStatus.pending);
        try {
            task.setDeviceIds(req.getDeviceIds() != null ? objectMapper.writeValueAsString(req.getDeviceIds()) : "[]");
        } catch (Exception e) {
            task.setDeviceIds("[]");
        }
        return taskRepository.save(task);
    }

    public TestTask getById(Long id) {
        TestTask task = taskRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        teamScope.assertTeamAccess(task.getTeamId());
        return task;
    }

    public Page<TestTask> list(int page, int pageSize, String status, Long createdBy) {
        TestTask.TaskStatus s = status != null && !status.isBlank() ? TestTask.TaskStatus.valueOf(status) : null;
        return taskRepository.findByFilters(s, createdBy, teamScope.scopeTeamId(), PageRequest.of(page - 1, pageSize));
    }

    public void submit(Long taskId) {
        TestTask task = getById(taskId);
        if ("visual".equals(task.getScriptType())) {
            validateVisualJson(task.getScriptContent());
        }
        appPackageService.assertAllowedForAutomation(task.getAppPackageId());
        environmentGovernanceService.assertTaskAllowed(task);
        task.setStatus(TestTask.TaskStatus.queued);
        taskRepository.save(task);
    }

    public TestTask update(Long id, CreateTaskRequest req) {
        TestTask task = getById(id);
        if (task.getStatus() == TestTask.TaskStatus.running) {
            throw new AppException("INVALID", "无法修改运行中的任务", HttpStatus.BAD_REQUEST);
        }
        if (req.getName() != null) task.setName(req.getName());
        if (req.getPlatform() != null) task.setPlatform(TestTask.TaskPlatform.valueOf(req.getPlatform()));
        if (req.getScriptType() != null) task.setScriptType(req.getScriptType());
        if (req.getScriptContent() != null) task.setScriptContent(req.getScriptContent());
        if (req.getAppPackage() != null) task.setAppPackage(req.getAppPackage());
        if (req.getParallelCount() != null) task.setParallelCount(req.getParallelCount());
        if (req.getTimeoutSeconds() != null) task.setTimeoutSeconds(req.getTimeoutSeconds());
        if (req.getEnableRecording() != null) task.setEnableRecording(req.getEnableRecording());
        return taskRepository.save(task);
    }

    public String previewVisualScript(String visualJson) {
        return visualScriptGenerator.generate(visualJson);
    }

    private void validateVisualJson(String json) {
        try {
            visualScriptGenerator.generate(json);
        } catch (Exception e) {
            throw new AppException("INVALID", "可视化步骤无效: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public void delete(Long id) {
        TestTask task = getById(id);
        if (task.getStatus() == TestTask.TaskStatus.running) {
            throw new AppException("INVALID", "无法删除运行中的任务", HttpStatus.BAD_REQUEST);
        }
        reportRepository.deleteAllByTaskId(id);
        reportRepository.findByTaskId(id).ifPresent(reportRepository::delete);
        entityManager.flush();
        taskDeletionService.deleteDependents(id);
        taskRepository.delete(task);
    }

    public List<TaskExecution> getExecutions(Long taskId) {
        return executionRepository.findByTaskId(taskId);
    }

    public List<ExecutionLog> getLogs(Long taskId, String logType, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return logRepository.findByTaskIdAndMessageContainingOrderByCreatedAtAsc(taskId, keyword);
        }
        if (logType != null && !logType.isBlank()) {
            return logRepository.findByTaskIdAndLogTypeOrderByCreatedAtAsc(taskId, logType);
        }
        return logRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    public void saveLog(Long taskId, Long executionId, Long deviceId, String logType, String level, String message) {
        ExecutionLog log = new ExecutionLog();
        log.setTaskId(taskId);
        log.setExecutionId(executionId);
        log.setDeviceId(deviceId);
        log.setLogType(logType);
        log.setLevel(level);
        log.setMessage(message);
        logRepository.save(log);
    }
}
