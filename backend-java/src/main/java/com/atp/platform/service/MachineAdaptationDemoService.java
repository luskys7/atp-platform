package com.atp.platform.service;

import com.atp.platform.entity.*;
import com.atp.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机型适配完整测试结果 Demo：用例 → 任务执行 → 日志 → 报告。
 */
@Service
@RequiredArgsConstructor
public class MachineAdaptationDemoService {

    public static final String DEMO_PREFIX = "[新品适配Demo]";

    private final MachineAdaptationService machineAdaptationService;
    private final CaseFolderRepository folderRepository;
    private final TestCaseRepository caseRepository;
    private final FunctionTagRepository tagRepository;
    private final CaseTagRelRepository caseTagRelRepository;
    private final TestTaskRepository taskRepository;
    private final TaskExecutionRepository executionRepository;
    private final ExecutionLogRepository logRepository;
    private final TestReportRepository reportRepository;
    private final DeviceRepository deviceRepository;
    private final TeamScopeService teamScope;
    private final UserRepository userRepository;

    /**
     * 一键加载完整结果 Demo（功能标签 + 用例 + 已完成任务/报告）。
     * 幂等：同名 Demo 用例/任务会复用并刷新报告。
     */
    @Transactional
    public Map<String, Object> loadFullResultDemo() {
        Map<String, Object> featureSummary = machineAdaptationService.loadDemoPackage();

        Long teamId = teamScope.teamIdForCreate(null);
        Long userId = resolveDemoUserId(teamId);
        Long folderId = resolveDemoFolderId(teamId);
        Long deviceId = ensureDemoDevice(teamId);

        List<DemoCaseSpec> specs = demoCaseSpecs();
        List<Map<String, Object>> caseViews = new ArrayList<>();
        List<Long> reportTaskIds = new ArrayList<>();
        int caseCreated = 0, caseUpdated = 0, taskSeeded = 0, reportSeeded = 0;

        for (DemoCaseSpec spec : specs) {
            TestCase c = findDemoCase(spec.name(), teamId);
            boolean isNew = c == null;
            if (isNew) {
                c = new TestCase();
                c.setName(spec.name());
                c.setTeamId(teamId);
                c.setFolderId(folderId);
                c.setOwnerId(userId);
                caseCreated++;
            } else {
                caseUpdated++;
            }
            c.setPlatform(TestCase.Platform.android);
            c.setScriptType("visual");
            c.setCaseStatus(TestCase.CaseStatus.active);
            c.setModuleName(spec.module());
            c.setPriority((byte) 1);
            c.setTags("新品适配,Demo," + spec.featureContent());
            c.setPreconditions("Demo 设备已连接；App 已登录");
            c.setExpectedResult(spec.expected());
            c.setStepsContent(spec.stepsJson());
            c.setAppPackage("com.demo.robot");
            c.setEnableRecording(false);
            c.setTimeoutSeconds(600);
            c.setDeletedAt(null);
            c = caseRepository.save(c);

            bindCaseTag(c.getId(), teamId, spec.featureContent());

            SeededRun run = seedTaskResult(c, spec, teamId, userId, deviceId);
            if (run.taskCreated) taskSeeded++;
            if (run.reportCreated) reportSeeded++;
            reportTaskIds.add(run.taskId);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("case_id", c.getId());
            row.put("case_name", c.getName());
            row.put("task_id", run.taskId);
            row.put("report_task_id", run.taskId);
            row.put("status", spec.success() ? "success" : "failed");
            row.put("pass_rate", spec.success() ? 100.0 : 0.0);
            caseViews.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feature_demo", featureSummary);
        result.put("case_created", caseCreated);
        result.put("case_updated", caseUpdated);
        result.put("task_seeded", taskSeeded);
        result.put("report_seeded", reportSeeded);
        result.put("demo_device_id", deviceId);
        result.put("cases", caseViews);
        result.put("report_task_ids", reportTaskIds);
        result.put("primary_report_task_id", reportTaskIds.isEmpty() ? null : reportTaskIds.get(0));
        result.put("hint", "已生成完整测试结果：可在「报告中心」或「任务列表」查看；任务页可勾选 Demo 功能集联动到这些用例");
        result.put("links", Map.of(
                "reports", "/reports",
                "tasks", "/tasks",
                "machine_adaptation", "/machine-adaptation"
        ));
        return result;
    }

    private Long resolveDemoUserId(Long teamId) {
        return userRepository.findByUsernameAndStatus("admin", (byte) 1)
                .map(User::getId)
                .orElse(1L);
    }

    private Long resolveDemoFolderId(Long teamId) {
        List<CaseFolder> folders = folderRepository.findByTeamId(teamId);
        if (folders != null && !folders.isEmpty()) {
            return folders.get(0).getId();
        }
        CaseFolder f = new CaseFolder();
        f.setName("新品适配Demo");
        f.setSortOrder(0);
        f.setTeamId(teamId);
        return folderRepository.save(f).getId();
    }

    private Long ensureDemoDevice(Long teamId) {
        String sn = "demo-adaptation-device";
        return deviceRepository.findBySerialNumber(sn).map(Device::getId).orElseGet(() -> {
            Device d = new Device();
            d.setSerialNumber(sn);
            d.setName("Demo机型适配设备");
            d.setPlatform(Device.Platform.android);
            d.setOsVersion("14");
            d.setModel("DemoPhone");
            d.setStatus(Device.DeviceStatus.offline);
            d.setTeamId(teamId);
            return deviceRepository.save(d).getId();
        });
    }

    private TestCase findDemoCase(String name, Long teamId) {
        return caseRepository.findByDeletedAtIsNull().stream()
                .filter(c -> name.equals(c.getName()))
                .filter(c -> teamId == null || teamId.equals(c.getTeamId()) || c.getTeamId() == null)
                .findFirst()
                .orElse(null);
    }

    private void bindCaseTag(Long caseId, Long teamId, String contentName) {
        List<FunctionTag> tags = tagRepository.findByContentNameAndTeamIdOrderByIdAsc(contentName, teamId);
        if (tags == null || tags.isEmpty()) {
            // 兼容 teamId 过滤为空的本地数据
            tags = tagRepository.findAll().stream()
                    .filter(t -> contentName.equals(t.getContentName()))
                    .filter(t -> t.getStatus() == null || t.getStatus() == 1)
                    .toList();
        }
        FunctionTag tag = tags.isEmpty() ? null : tags.get(0);
        if (tag == null) {
            return;
        }
        boolean exists = caseTagRelRepository.findByCaseId(caseId).stream()
                .anyMatch(r -> tag.getId().equals(r.getTagId()));
        if (!exists) {
            CaseTagRel rel = new CaseTagRel();
            rel.setCaseId(caseId);
            rel.setTagId(tag.getId());
            caseTagRelRepository.save(rel);
        }
    }

    private SeededRun seedTaskResult(TestCase c, DemoCaseSpec spec, Long teamId, Long userId, Long deviceId) {
        String taskName = DEMO_PREFIX + " " + spec.shortTitle();
        TestTask task = taskRepository.findAll().stream()
                .filter(t -> taskName.equals(t.getName()))
                .findFirst()
                .orElse(null);
        boolean taskCreated = false;
        LocalDateTime started = LocalDateTime.now().minusMinutes(spec.success() ? 18 : 12);
        LocalDateTime finished = started.plusMinutes(spec.success() ? 3 : 2);

        if (task == null) {
            task = new TestTask();
            task.setName(taskName);
            taskCreated = true;
        }
        task.setDescription("机型适配完整结果 Demo · " + spec.featureContent());
        task.setPlatform(TestTask.TaskPlatform.android);
        task.setScriptType("visual");
        task.setScriptContent(c.getStepsContent());
        task.setAppPackage(c.getAppPackage());
        task.setPriority((byte) 5);
        task.setStatus(spec.success() ? TestTask.TaskStatus.success : TestTask.TaskStatus.failed);
        task.setDeviceIds("[" + deviceId + "]");
        task.setParallelCount(1);
        task.setTimeoutSeconds(600);
        task.setRetryCount(0);
        task.setMaxRetries(0);
        task.setEnableRecording(false);
        task.setCreatedBy(userId);
        task.setTeamId(teamId);
        task.setStartedAt(started);
        task.setFinishedAt(finished);
        task.setSourceCaseId(c.getId());
        if (!spec.success()) {
            task.setErrorCode("ASSERT");
            task.setErrorMessage(spec.failReason());
        } else {
            task.setErrorCode(null);
            task.setErrorMessage(null);
        }
        task = taskRepository.save(task);

        // 清理旧执行/日志，重建一致结果
        List<TaskExecution> oldExecs = executionRepository.findByTaskId(task.getId());
        for (TaskExecution e : oldExecs) {
            executionRepository.delete(e);
        }
        logRepository.deleteByTaskId(task.getId());

        TaskExecution exec = new TaskExecution();
        exec.setTaskId(task.getId());
        exec.setDeviceId(deviceId);
        exec.setStatus(spec.success() ? TestTask.TaskStatus.success : TestTask.TaskStatus.failed);
        exec.setStartedAt(started);
        exec.setFinishedAt(finished);
        exec.setResultSummary(spec.success()
                ? "全部步骤通过"
                : "步骤失败: " + spec.failReason());
        if (!spec.success()) {
            exec.setErrorCode("ASSERT");
            exec.setErrorMessage(spec.failReason());
            exec.setFailedStepIndex(2);
        }
        exec = executionRepository.save(exec);

        writeLogs(task.getId(), exec.getId(), deviceId, spec);

        boolean reportCreated = false;
        TestReport report = reportRepository.findByTaskId(task.getId()).orElse(null);
        if (report == null) {
            report = new TestReport();
            report.setTaskId(task.getId());
            reportCreated = true;
        }
        report.setTitle(task.getName() + " - 测试报告");
        report.setTotalExecutions(1);
        report.setSuccessCount(spec.success() ? 1 : 0);
        report.setFailedCount(spec.success() ? 0 : 1);
        report.setPassRate(spec.success() ? 100.0 : 0.0);
        report.setSummary(spec.success()
                ? String.format("共 1 次执行，成功 1，业务失败 0，通过率 100.00%% · 功能项「%s」", spec.featureContent())
                : String.format("共 1 次执行，成功 0，业务失败 1，通过率 0.00%% · 失败原因：%s", spec.failReason()));
        report.setModuleName(spec.module());
        report.setVersionLabel("Demo-v1.0");
        report.setProjectCode("MACHINE-ADAPT");
        report.setOperatorLabel("超级管理员");
        report.setDeviceLabel("demo-adaptation-device");
        report.setArchived(false);
        reportRepository.save(report);

        return new SeededRun(task.getId(), taskCreated, reportCreated);
    }

    private void writeLogs(Long taskId, Long executionId, Long deviceId, DemoCaseSpec spec) {
        saveLog(taskId, executionId, deviceId, "platform", "info", "任务开始执行（Demo）");
        saveLog(taskId, executionId, deviceId, "script", "info", "ATP_STEP_START|1|启动应用");
        saveLog(taskId, executionId, deviceId, "script", "info", "ATP_STEP_END|1|pass|启动应用成功");
        saveLog(taskId, executionId, deviceId, "script", "info", "ATP_STEP_START|2|" + spec.step2());
        if (spec.success()) {
            saveLog(taskId, executionId, deviceId, "script", "info", "ATP_STEP_END|2|pass|" + spec.step2() + "成功");
            saveLog(taskId, executionId, deviceId, "script", "info", "ATP_STEP_START|3|结果校验");
            saveLog(taskId, executionId, deviceId, "script", "info", "ATP_STEP_END|3|pass|断言通过");
            saveLog(taskId, executionId, deviceId, "platform", "info", "任务执行成功");
        } else {
            saveLog(taskId, executionId, deviceId, "script", "error",
                    "ATP_STEP_END|2|fail|" + spec.failReason());
            saveLog(taskId, executionId, deviceId, "platform", "error", "任务执行失败: " + spec.failReason());
        }
    }

    private void saveLog(Long taskId, Long executionId, Long deviceId, String type, String level, String message) {
        ExecutionLog log = new ExecutionLog();
        log.setTaskId(taskId);
        log.setExecutionId(executionId);
        log.setDeviceId(deviceId);
        log.setLogType(type);
        log.setLevel(level);
        log.setMessage(message);
        logRepository.save(log);
    }

    private List<DemoCaseSpec> demoCaseSpecs() {
        return List.of(
                new DemoCaseSpec(
                        DEMO_PREFIX + " 清扫-开始清洁",
                        "开始清洁",
                        "清洁功能",
                        "清扫",
                        "进入清洁并成功启动清扫任务",
                        true,
                        null,
                        "点击开始清洁",
                        stepsJson("启动扫地机 App", "点击开始清洁", "断言任务状态为清洁中")
                ),
                new DemoCaseSpec(
                        DEMO_PREFIX + " 设置-通用勿扰模式",
                        "通用勿扰",
                        "设备管理",
                        "通用-勿扰模式",
                        "打开勿扰模式并保存成功",
                        true,
                        null,
                        "打开勿扰模式开关",
                        stepsJson("进入设置-通用", "打开勿扰模式", "断言开关状态为开")
                ),
                new DemoCaseSpec(
                        DEMO_PREFIX + " 设置-鲸灵托管扫地吸力",
                        "鲸灵托管吸力",
                        "设备管理",
                        "鲸灵托管-扫地吸力",
                        "调整扫地吸力后生效",
                        false,
                        "未找到吸力档位控件 / 断言失败",
                        "选择强力吸力",
                        stepsJson("进入设置-鲸灵托管", "选择强力吸力", "断言当前吸力为强力")
                ),
                new DemoCaseSpec(
                        DEMO_PREFIX + " 基站-设备面板集尘",
                        "基站集尘",
                        "清洁功能",
                        "设备面板-基站集尘",
                        "触发基站集尘并完成",
                        true,
                        null,
                        "触发基站集尘",
                        stepsJson("进入设备面板", "触发基站集尘", "断言集尘完成提示出现")
                )
        );
    }

    private static String stepsJson(String s1, String s2, String s3) {
        return "{\"version\":1,\"steps\":["
                + "{\"action\":\"launch\",\"desc\":\"" + s1 + "\"},"
                + "{\"action\":\"click\",\"desc\":\"" + s2 + "\"},"
                + "{\"action\":\"assert\",\"desc\":\"" + s3 + "\"}"
                + "]}";
    }

    private record DemoCaseSpec(
            String name,
            String shortTitle,
            String module,
            String featureContent,
            String expected,
            boolean success,
            String failReason,
            String step2,
            String stepsJson
    ) {}

    private record SeededRun(Long taskId, boolean taskCreated, boolean reportCreated) {}
}
