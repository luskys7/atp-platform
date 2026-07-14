package com.atp.platform.service;

import com.atp.platform.entity.*;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.TestCaseRepository;
import com.atp.platform.repository.TestSuiteItemRepository;
import com.atp.platform.repository.TestSuiteRepository;
import com.atp.platform.repository.SuiteRunRepository;
import com.atp.platform.repository.SuiteRunItemRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SuiteService {

    private final TestSuiteRepository suiteRepository;
    private final TestSuiteItemRepository itemRepository;
    private final TestCaseRepository caseRepository;
    private final CheckpointService checkpointService;
    private final RecycleBinService recycleBinService;
    private final SuiteRunRepository runRepository;
    private final SuiteRunItemRepository runItemRepository;
    private final TaskService taskService;
    private final SchedulerService schedulerService;
    private final ObjectMapper objectMapper;
    private final TeamScopeService teamScope;

    public List<TestSuite> list() {
        Long teamId = teamScope.scopeTeamId();
        if (teamId == null) {
            return suiteRepository.findByDeletedAtIsNullOrderByUpdatedAtDesc();
        }
        return suiteRepository.findByDeletedAtIsNullAndTeamIdOrderByUpdatedAtDesc(teamId);
    }

    public Map<String, Object> getDetail(Long id) {
        TestSuite suite = get(id);
        List<TestSuiteItem> items = itemRepository.findBySuiteIdOrderBySortOrderAsc(id);
        List<Map<String, Object>> caseList = new ArrayList<>();
        for (TestSuiteItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item_id", item.getId());
            row.put("case_id", item.getCaseId());
            row.put("sort_order", item.getSortOrder());
            row.put("enabled", item.getEnabled());
            caseRepository.findById(item.getCaseId()).ifPresent(c -> {
                row.put("case_name", c.getName());
                row.put("case_status", c.getCaseStatus());
            });
            caseList.add(row);
        }
        return Map.of("suite", suite, "items", caseList);
    }

    public TestSuite get(Long id) {
        TestSuite s = suiteRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "套件不存在", HttpStatus.NOT_FOUND));
        if (s.getDeletedAt() != null) {
            throw new AppException("NOT_FOUND", "套件已删除", HttpStatus.NOT_FOUND);
        }
        teamScope.assertTeamAccess(s.getTeamId());
        return s;
    }

    @Transactional
    public TestSuite create(Map<String, Object> body, Long userId) {
        TestSuite s = mapSuite(new TestSuite(), body);
        s.setCreatedBy(userId);
        Long reqTeam = body.get("team_id") != null ? Long.valueOf(body.get("team_id").toString()) : null;
        s.setTeamId(teamScope.teamIdForCreate(reqTeam));
        s = suiteRepository.save(s);
        saveItems(s.getId(), body.get("items"));
        return s;
    }

    @Transactional
    public TestSuite update(Long id, Map<String, Object> body) {
        TestSuite s = get(id);
        mapSuite(s, body);
        s = suiteRepository.save(s);
        if (body.containsKey("items")) {
            itemRepository.deleteBySuiteId(id);
            saveItems(id, body.get("items"));
        }
        return s;
    }

    @Transactional
    public void delete(Long id, Long userId) {
        TestSuite s = get(id);
        s.setDeletedAt(LocalDateTime.now());
        suiteRepository.save(s);
        recycleBinService.add("test_suite", id, s.getName(), s, userId);
    }

    @Transactional
    public Map<String, Object> runSuite(Long id, Long userId) {
        TestSuite suite = get(id);
        List<TestSuiteItem> items = itemRepository.findBySuiteIdOrderBySortOrderAsc(id);
        List<TestSuiteItem> enabled = items.stream().filter(i -> Boolean.TRUE.equals(i.getEnabled())).toList();

        SuiteRun run = new SuiteRun();
        run.setSuiteId(id);
        run.setStatus(SuiteRun.RunStatus.running);
        run.setCreatedBy(userId);
        run.setConfigSnapshotJson(buildConfigSnapshot(suite, items));
        run = runRepository.save(run);

        List<TestTask> tasks = new ArrayList<>();
        int runnableCount = 0;
        int order = 0;

        boolean serial = suite.getExecMode() == TestSuite.ExecMode.serial;

        if (suite.getHookBefore() != null && !suite.getHookBefore().isBlank()) {
            TestTask hookTask = checkpointService.createHookTask(suite, suite.getHookBefore(), "before", run.getId(), userId);
            taskService.submit(hookTask.getId());
            schedulerService.enqueueTask(hookTask.getId(), (byte) 1);
            tasks.add(hookTask);
        }

        for (TestSuiteItem item : enabled) {
            TestCase testCase = caseRepository.findById(item.getCaseId())
                    .orElseThrow(() -> new AppException("NOT_FOUND", "用例不存在: " + item.getCaseId(), HttpStatus.NOT_FOUND));

            SuiteRunItem runItem = new SuiteRunItem();
            runItem.setRunId(run.getId());
            runItem.setCaseId(item.getCaseId());
            runItem.setSortOrder(order++);

            if (testCase.getDeletedAt() != null) {
                runItem.setStatus(SuiteRunItem.ItemStatus.skipped);
                runItem.setErrorMessage("用例已删除");
                runItemRepository.save(runItem);
                continue;
            }
            if (testCase.getCaseStatus() != TestCase.CaseStatus.active) {
                runItem.setStatus(SuiteRunItem.ItemStatus.skipped);
                runItem.setErrorMessage("仅生效状态用例可执行，当前: " + testCase.getCaseStatus());
                runItemRepository.save(runItem);
                continue;
            }

            runItem.setStatus(serial ? SuiteRunItem.ItemStatus.pending : SuiteRunItem.ItemStatus.running);
            runItem = runItemRepository.save(runItem);
            runnableCount++;

            if (serial) {
                continue;
            }

            try {
                TestTask task = checkpointService.createTaskFromCase(testCase, userId, run.getId(), runItem.getId(), null, null);
                runItem.setTaskId(task.getId());
                runItemRepository.save(runItem);
                taskService.submit(task.getId());
                schedulerService.enqueueTask(task.getId(), task.getPriority());
                tasks.add(task);
            } catch (Exception e) {
                runItem.setStatus(SuiteRunItem.ItemStatus.failed);
                runItem.setErrorMessage(e.getMessage());
                runItemRepository.save(runItem);
                if (suite.getFailPolicy() == TestSuite.FailPolicy.stop) {
                    run.setStatus(SuiteRun.RunStatus.failed);
                    run.setFinishedAt(LocalDateTime.now());
                    run.setTotalItems(runnableCount);
                    runRepository.save(run);
                    throw new AppException("SUITE_ABORT", "套件执行中止: " + e.getMessage(), HttpStatus.BAD_REQUEST);
                }
            }
        }

        if (serial && (suite.getHookBefore() == null || suite.getHookBefore().isBlank())) {
            checkpointService.tryDispatchNextSuiteItem(run.getId(), userId);
        }

        run.setTotalItems(runnableCount);
        runRepository.save(run);
        return Map.of("suite_run_id", run.getId(), "tasks", tasks, "total", runnableCount, "skipped", enabled.size() - runnableCount);
    }

    @Transactional
    public TestSuite restoreFromRunSnapshot(Long runId) {
        SuiteRun run = runRepository.findById(runId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "执行批次不存在", HttpStatus.NOT_FOUND));
        if (run.getConfigSnapshotJson() == null || run.getConfigSnapshotJson().isBlank()) {
            throw new AppException("NOT_FOUND", "该批次无配置快照", HttpStatus.NOT_FOUND);
        }
        try {
            Map<String, Object> snap = objectMapper.readValue(run.getConfigSnapshotJson(), new TypeReference<>() {});
            TestSuite suite = get(run.getSuiteId());
            @SuppressWarnings("unchecked")
            Map<String, Object> suiteMap = (Map<String, Object>) snap.get("suite");
            if (suiteMap != null) {
                mapSuite(suite, suiteMap);
                suiteRepository.save(suite);
            }
            if (snap.containsKey("items")) {
                itemRepository.deleteBySuiteId(suite.getId());
                saveItems(suite.getId(), snap.get("items"));
            }
            return suite;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("INVALID", "还原配置失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public List<SuiteRun> listRuns(Long suiteId) {
        return checkpointService.listRuns(suiteId);
    }

    private String buildConfigSnapshot(TestSuite suite, List<TestSuiteItem> items) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("suite", suite);
            List<Map<String, Object>> itemRows = new ArrayList<>();
            for (TestSuiteItem item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("case_id", item.getCaseId());
                row.put("sort_order", item.getSortOrder());
                row.put("enabled", item.getEnabled());
                itemRows.add(row);
            }
            snap.put("items", itemRows);
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveItems(Long suiteId, Object itemsObj) {
        if (!(itemsObj instanceof List<?> list)) return;
        int order = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            TestSuiteItem item = new TestSuiteItem();
            item.setSuiteId(suiteId);
            item.setCaseId(Long.valueOf(m.get("case_id").toString()));
            item.setSortOrder(m.containsKey("sort_order") ? Integer.valueOf(m.get("sort_order").toString()) : order++);
            item.setEnabled(!m.containsKey("enabled") || Boolean.parseBoolean(m.get("enabled").toString()));
            itemRepository.save(item);
        }
    }

    private TestSuite mapSuite(TestSuite s, Map<String, Object> body) {
        if (body.containsKey("name")) s.setName(body.get("name").toString());
        if (body.containsKey("description")) s.setDescription(str(body.get("description")));
        if (body.containsKey("tags")) s.setTags(str(body.get("tags")));
        if (body.containsKey("env_id") && body.get("env_id") != null) {
            s.setEnvId(Long.valueOf(body.get("env_id").toString()));
        }
        if (body.containsKey("exec_mode")) s.setExecMode(TestSuite.ExecMode.valueOf(str(body.get("exec_mode"))));
        if (body.containsKey("fail_policy")) s.setFailPolicy(TestSuite.FailPolicy.valueOf(str(body.get("fail_policy"))));
        if (body.containsKey("hook_before")) s.setHookBefore(str(body.get("hook_before")));
        if (body.containsKey("hook_after")) s.setHookAfter(str(body.get("hook_after")));
        return s;
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
