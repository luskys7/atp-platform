package com.atp.platform.service;

import com.atp.platform.dto.CreateTaskRequest;
import com.atp.platform.entity.*;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CheckpointService {

    private static final Pattern CHECKPOINT_PATTERN = Pattern.compile("(?m)^CHECKPOINT_FAILED:step=(\\d+)\\s*$");

    private final SuiteRunRepository runRepository;
    private final SuiteRunItemRepository itemRepository;
    private final TestTaskRepository taskRepository;
    private final TaskExecutionRepository executionRepository;
    private final ExecutionLogRepository logRepository;
    private final TestCaseRepository caseRepository;
    private final TestSuiteRepository suiteRepository;
    private final TaskService taskService;
    private final ObjectProvider<SchedulerService> schedulerServiceProvider;

    public Map<String, Object> getRunDetail(Long runId) {
        SuiteRun run = runRepository.findById(runId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "执行批次不存在", HttpStatus.NOT_FOUND));
        List<SuiteRunItem> items = itemRepository.findByRunIdOrderBySortOrderAsc(runId);
        List<Map<String, Object>> itemViews = new ArrayList<>();
        for (SuiteRunItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("case_id", item.getCaseId());
            row.put("sort_order", item.getSortOrder());
            row.put("status", item.getStatus());
            row.put("task_id", item.getTaskId());
            row.put("failed_step_index", item.getFailedStepIndex());
            row.put("error_message", item.getErrorMessage());
            caseRepository.findById(item.getCaseId()).ifPresent(c -> row.put("case_name", c.getName()));
            itemViews.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", run);
        result.put("items", itemViews);
        result.put("has_snapshot", run.getConfigSnapshotJson() != null && !run.getConfigSnapshotJson().isBlank());
        return result;
    }

    public List<SuiteRun> listRuns(Long suiteId) {
        return runRepository.findBySuiteIdOrderByStartedAtDesc(suiteId);
    }

    @Transactional
    public Map<String, Object> resumeSuiteRun(Long runId, Long userId) {
        SuiteRun run = runRepository.findById(runId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "执行批次不存在", HttpStatus.NOT_FOUND));
        if (run.getStatus() == SuiteRun.RunStatus.completed) {
            throw new AppException("INVALID", "批次已完成，无需续跑", HttpStatus.BAD_REQUEST);
        }
        if (run.getStatus() == SuiteRun.RunStatus.paused) {
            run.setStatus(SuiteRun.RunStatus.running);
            runRepository.save(run);
            return Map.of("run_id", runId, "resumed_count", 0, "message", "批次已从暂停恢复");
        }
        List<SuiteRunItem> items = itemRepository.findByRunIdOrderBySortOrderAsc(runId);
        List<TestTask> tasks = new ArrayList<>();
        int resumed = 0;
        TestSuite suite = suiteRepository.findById(run.getSuiteId()).orElse(null);
        boolean serial = suite != null && suite.getExecMode() == TestSuite.ExecMode.serial;

        for (SuiteRunItem item : items) {
            if (item.getStatus() == SuiteRunItem.ItemStatus.success) continue;
            if (item.getStatus() == SuiteRunItem.ItemStatus.skipped) continue;
            TestCase testCase = caseRepository.findById(item.getCaseId())
                    .orElseThrow(() -> new AppException("NOT_FOUND", "用例不存在", HttpStatus.NOT_FOUND));
            Integer fromStep = item.getFailedStepIndex();
            TestTask task = createTaskFromCase(testCase, userId, run.getId(), item.getId(), fromStep, null);
            item.setStatus(SuiteRunItem.ItemStatus.running);
            item.setTaskId(task.getId());
            item.setErrorMessage(null);
            itemRepository.save(item);
            taskService.submit(task.getId());
            schedulerServiceProvider.getObject().enqueueTask(task.getId(), task.getPriority());
            tasks.add(task);
            resumed++;
            if (serial) break;
        }
        run.setStatus(SuiteRun.RunStatus.running);
        run.setFinishedAt(null);
        runRepository.save(run);
        return Map.of("run_id", runId, "resumed_count", resumed, "tasks", tasks);
    }

    @Transactional
    public void pauseSuiteRun(Long runId) {
        SuiteRun run = runRepository.findById(runId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "执行批次不存在", HttpStatus.NOT_FOUND));
        if (run.getStatus() != SuiteRun.RunStatus.running) {
            throw new AppException("INVALID", "仅运行中批次可暂停", HttpStatus.BAD_REQUEST);
        }
        run.setStatus(SuiteRun.RunStatus.paused);
        runRepository.save(run);
    }

    @Transactional
    public TestTask resumeTask(Long taskId, Integer fromStep, Long userId) {
        TestTask orig = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        if (fromStep == null) {
            fromStep = detectFailedStep(taskId);
        }
        if (fromStep == null || fromStep < 1) {
            fromStep = 1;
        }
        CreateTaskRequest req = new CreateTaskRequest();
        req.setName("[续跑] " + orig.getName());
        req.setPlatform(orig.getPlatform().name());
        req.setScriptType(orig.getScriptType());
        req.setScriptContent(orig.getScriptContent());
        req.setAppPackage(orig.getAppPackage());
        req.setAppPath(orig.getAppPath());
        req.setParallelCount(orig.getParallelCount());
        req.setTimeoutSeconds(orig.getTimeoutSeconds());
        req.setEnableRecording(orig.getEnableRecording());
        req.setPriority(orig.getPriority());
        TestTask task = taskService.create(req, userId);
        task.setResumeFromStep(fromStep);
        task.setParentTaskId(taskId);
        task.setSourceCaseId(orig.getSourceCaseId());
        task.setSuiteRunId(orig.getSuiteRunId());
        task.setSuiteRunItemId(orig.getSuiteRunItemId());
        task.setAppPackageId(orig.getAppPackageId());
        taskRepository.save(task);
        taskService.saveLog(taskId, null, null, "platform", "info",
                "断点续跑：从步骤 " + fromStep + " 创建任务 #" + task.getId());
        taskService.submit(task.getId());
        schedulerServiceProvider.getObject().enqueueTask(task.getId(), task.getPriority());
        return task;
    }

    public TestTask createTaskFromCase(TestCase c, Long userId, Long runId, Long runItemId,
                                       Integer resumeFromStep, Long appPackageId) {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setName("[用例] " + c.getName() + (resumeFromStep != null && resumeFromStep > 1 ? " (续跑@" + resumeFromStep + ")" : ""));
        req.setPlatform(c.getPlatform().name());
        req.setScriptType(c.getScriptType());
        req.setScriptContent(c.getStepsContent());
        req.setAppPackage(c.getAppPackage());
        req.setParallelCount(1);
        req.setTimeoutSeconds(c.getTimeoutSeconds());
        req.setEnableRecording(c.getEnableRecording());
        req.setPriority(c.getPriority());
        TestTask task = taskService.create(req, userId);
        task.setSourceCaseId(c.getId());
        task.setSuiteRunId(runId);
        task.setSuiteRunItemId(runItemId);
        task.setResumeFromStep(resumeFromStep);
        task.setAppPackageId(appPackageId);
        task.setEnvId(c.getEnvId());
        task.setDatasetId(c.getDatasetId());
        task.setTeamId(c.getTeamId());
        if (runId != null) {
            runRepository.findById(runId).ifPresent(run -> {
                if (run.getSuiteId() != null) {
                    suiteRepository.findById(run.getSuiteId()).ifPresent(suite -> {
                        if (suite.getTags() != null && !suite.getTags().isBlank()) {
                            task.setDeviceTags(suite.getTags());
                        }
                        if (task.getEnvId() == null && suite.getEnvId() != null) {
                            task.setEnvId(suite.getEnvId());
                        }
                    });
                }
            });
        }
        return taskRepository.save(task);
    }

    public TestTask createHookTask(TestSuite suite, String hookContent, String phase, Long runId, Long userId) {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setName("[套件" + ("before".equals(phase) ? "前置" : "后置") + "] " + suite.getName());
        req.setPlatform(TestTask.TaskPlatform.android.name());
        req.setScriptType("visual");
        req.setScriptContent(normalizeHookContent(hookContent));
        req.setParallelCount(1);
        req.setTimeoutSeconds(3600);
        req.setEnableRecording(false);
        req.setPriority((byte) 1);
        TestTask task = taskService.create(req, userId != null ? userId : 1L);
        task.setSuiteRunId(runId);
        task.setSuiteHookPhase(phase);
        task.setTeamId(suite.getTeamId());
        return taskRepository.save(task);
    }

    @Transactional
    public void onTaskFinished(Long taskId, boolean success) {
        TestTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null || task.getSuiteRunId() == null) return;

        if (task.getSuiteHookPhase() != null) {
            handleHookFinished(task, success);
            return;
        }

        SuiteRunItem item = task.getSuiteRunItemId() != null
                ? itemRepository.findById(task.getSuiteRunItemId()).orElse(null)
                : itemRepository.findByTaskId(taskId).orElse(null);
        if (item == null) return;

        if (success) {
            item.setStatus(SuiteRunItem.ItemStatus.success);
            item.setFailedStepIndex(null);
            item.setErrorMessage(null);
        } else {
            item.setStatus(SuiteRunItem.ItemStatus.failed);
            Integer step = detectFailedStep(taskId);
            item.setFailedStepIndex(step);
            taskRepository.findById(taskId).ifPresent(t -> item.setErrorMessage(t.getErrorMessage()));
        }
        itemRepository.save(item);
        if (isSerialRun(task.getSuiteRunId())) {
            tryDispatchNextSuiteItem(task.getSuiteRunId(), task.getCreatedBy());
        }
        refreshRunStats(task.getSuiteRunId());
    }

    /** 串行套件：前置钩子完成后调度首个用例 */
    public void tryDispatchNextSuiteItem(Long runId, Long userId) {
        SuiteRun run = runRepository.findById(runId).orElse(null);
        if (run == null || run.getStatus() == SuiteRun.RunStatus.completed) return;
        if (run.getStatus() == SuiteRun.RunStatus.paused) return;
        TestSuite suite = suiteRepository.findById(run.getSuiteId()).orElse(null);
        if (suite == null || suite.getExecMode() != TestSuite.ExecMode.serial) return;
        if (itemRepository.existsByRunIdAndStatus(runId, SuiteRunItem.ItemStatus.running)) return;
        if (suite.getFailPolicy() == TestSuite.FailPolicy.stop
                && itemRepository.existsByRunIdAndStatus(runId, SuiteRunItem.ItemStatus.failed)) {
            return;
        }
        SuiteRunItem next = itemRepository.findFirstByRunIdAndStatusOrderBySortOrderAsc(runId, SuiteRunItem.ItemStatus.pending)
                .orElse(null);
        if (next == null) return;

        TestCase testCase = caseRepository.findById(next.getCaseId()).orElse(null);
        if (testCase == null || testCase.getDeletedAt() != null) {
            next.setStatus(SuiteRunItem.ItemStatus.skipped);
            next.setErrorMessage("用例不可用");
            itemRepository.save(next);
            tryDispatchNextSuiteItem(runId, userId);
            return;
        }
        if (testCase.getCaseStatus() != TestCase.CaseStatus.active) {
            next.setStatus(SuiteRunItem.ItemStatus.skipped);
            next.setErrorMessage("用例非生效状态");
            itemRepository.save(next);
            tryDispatchNextSuiteItem(runId, userId);
            return;
        }

        TestTask task = createTaskFromCase(testCase, userId != null ? userId : run.getCreatedBy(),
                runId, next.getId(), null, null);
        next.setStatus(SuiteRunItem.ItemStatus.running);
        next.setTaskId(task.getId());
        itemRepository.save(next);
        taskService.submit(task.getId());
        schedulerServiceProvider.getObject().enqueueTask(task.getId(), task.getPriority());
    }

    private boolean isSerialRun(Long runId) {
        if (runId == null) return false;
        return runRepository.findById(runId)
                .flatMap(r -> suiteRepository.findById(r.getSuiteId()))
                .map(s -> s.getExecMode() == TestSuite.ExecMode.serial)
                .orElse(false);
    }

    public void recordExecutionFailure(Long taskId, Long executionId, String errorMessage, List<PythonExecutorClient.LogEntry> logs) {
        Integer step = parseFailedStepFromLogs(logs);
        if (step == null) step = parseFailedStepFromText(errorMessage);
        if (step != null) {
            final Integer failedStep = step;
            executionRepository.findById(executionId).ifPresent(exec -> {
                exec.setFailedStepIndex(failedStep);
                executionRepository.save(exec);
            });
        }
    }

    public Integer detectFailedStep(Long taskId) {
        List<TaskExecution> execs = executionRepository.findByTaskId(taskId);
        for (TaskExecution exec : execs) {
            if (exec.getFailedStepIndex() != null) return exec.getFailedStepIndex();
        }
        for (ExecutionLog log : logRepository.findByTaskIdOrderByCreatedAtAsc(taskId)) {
            Integer step = parseFailedStepFromText(log.getMessage());
            if (step != null) return step;
        }
        return null;
    }

    private void handleHookFinished(TestTask task, boolean success) {
        String label = "before".equals(task.getSuiteHookPhase()) ? "前置" : "后置";
        taskService.saveLog(task.getId(), null, null, "platform", success ? "info" : "error",
                "套件" + label + "钩子执行" + (success ? "完成" : "失败"));
        if ("before".equals(task.getSuiteHookPhase()) && !success) {
            taskService.saveLog(task.getId(), null, null, "platform", "warn", "前置钩子失败，套件用例仍将继续执行");
        }
        if ("before".equals(task.getSuiteHookPhase())) {
            tryDispatchNextSuiteItem(task.getSuiteRunId(), task.getCreatedBy());
        }
    }

    private void tryTriggerHookAfter(Long runId) {
        SuiteRun run = runRepository.findById(runId).orElse(null);
        if (run == null || Boolean.TRUE.equals(run.getHookAfterTriggered())) return;

        List<SuiteRunItem> items = itemRepository.findByRunIdOrderBySortOrderAsc(runId);
        boolean anyActive = items.stream().anyMatch(i ->
                i.getStatus() == SuiteRunItem.ItemStatus.pending || i.getStatus() == SuiteRunItem.ItemStatus.running);
        if (anyActive) return;

        TestSuite suite = suiteRepository.findById(run.getSuiteId()).orElse(null);
        if (suite == null || suite.getHookAfter() == null || suite.getHookAfter().isBlank()) return;

        run.setHookAfterTriggered(true);
        runRepository.save(run);

        TestTask hookTask = createHookTask(suite, suite.getHookAfter(), "after", runId, run.getCreatedBy());
        taskService.submit(hookTask.getId());
        schedulerServiceProvider.getObject().enqueueTask(hookTask.getId(), (byte) 1);
    }

    private void refreshRunStats(Long runId) {
        SuiteRun run = runRepository.findById(runId).orElse(null);
        if (run == null) return;
        List<SuiteRunItem> items = itemRepository.findByRunIdOrderBySortOrderAsc(runId);
        int success = 0, failed = 0, pending = 0;
        for (SuiteRunItem item : items) {
            switch (item.getStatus()) {
                case success -> success++;
                case failed -> failed++;
                case pending, running -> pending++;
                default -> {}
            }
        }
        run.setSuccessCount(success);
        run.setFailedCount(failed);
        if (pending == 0) {
            run.setStatus(failed > 0 ? SuiteRun.RunStatus.failed : SuiteRun.RunStatus.completed);
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
            tryTriggerHookAfter(runId);
        } else {
            run.setStatus(SuiteRun.RunStatus.running);
            runRepository.save(run);
        }
    }

    private String normalizeHookContent(String content) {
        if (content == null || content.isBlank()) return "{\"steps\":[]}";
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) return trimmed;
        return "{\"steps\":" + trimmed + "}";
    }

    private Integer parseFailedStepFromLogs(List<PythonExecutorClient.LogEntry> logs) {
        if (logs == null) return null;
        for (PythonExecutorClient.LogEntry log : logs) {
            Integer step = parseFailedStepFromText(log.message());
            if (step != null) return step;
        }
        return null;
    }

    private Integer parseFailedStepFromText(String text) {
        if (text == null) return null;
        Matcher m = CHECKPOINT_PATTERN.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }
}
