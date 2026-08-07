package com.atp.platform.service;

import com.atp.platform.dto.CreateTaskRequest;
import com.atp.platform.entity.*;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CaseCatalogService {

    private final CaseFolderRepository folderRepository;
    private final TestCaseRepository caseRepository;
    private final TestCaseVersionRepository versionRepository;
    private final TestSuiteItemRepository suiteItemRepository;
    private final RecycleBinService recycleBinService;
    private final TaskService taskService;
    private final TestTaskRepository taskRepository;
    private final SchedulerService schedulerService;
    private final ObjectMapper objectMapper;
    private final TeamScopeService teamScope;

    // --- Folders ---

    public List<Map<String, Object>> listFolderTree() {
        List<CaseFolder> all = teamScope.scopeTeamId() == null
                ? folderRepository.findAll()
                : folderRepository.findByTeamId(teamScope.scopeTeamId());
        Map<Long, List<CaseFolder>> byParent = new HashMap<>();
        for (CaseFolder f : all) {
            Long pid = f.getParentId() == null ? 0L : f.getParentId();
            byParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(f);
        }
        byParent.values().forEach(list -> list.sort(Comparator.comparingInt(f -> f.getSortOrder() != null ? f.getSortOrder() : 0)));
        return buildTree(byParent, 0L);
    }

    private List<Map<String, Object>> buildTree(Map<Long, List<CaseFolder>> byParent, Long parentId) {
        List<CaseFolder> children = byParent.getOrDefault(parentId, List.of());
        List<Map<String, Object>> result = new ArrayList<>();
        for (CaseFolder f : children) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", f.getId());
            node.put("name", f.getName());
            node.put("parent_id", f.getParentId());
            node.put("sort_order", f.getSortOrder());
            node.put("children", buildTree(byParent, f.getId()));
            result.add(node);
        }
        return result;
    }

    @Transactional
    public CaseFolder createFolder(String name, Long parentId) {
        if (parentId != null) {
            folderRepository.findById(parentId)
                    .orElseThrow(() -> new AppException("NOT_FOUND", "上级目录不存在", HttpStatus.NOT_FOUND));
        }
        CaseFolder f = new CaseFolder();
        f.setName(name);
        f.setParentId(parentId);
        f.setSortOrder(nextSortOrder(parentId));
        f.setTeamId(teamScope.teamIdForCreate(null));
        return folderRepository.save(f);
    }

    @Transactional
    public CaseFolder updateFolder(Long id, String name, Long parentId) {
        CaseFolder f = getFolder(id);
        f.setName(name);
        if (parentId != null && parentId.equals(id)) {
            throw new AppException("INVALID", "不能将目录移动到自身", HttpStatus.BAD_REQUEST);
        }
        if (parentId != null && isDescendant(id, parentId)) {
            throw new AppException("INVALID", "不能将目录移动到其子目录下", HttpStatus.BAD_REQUEST);
        }
        if (parentId != null) {
            folderRepository.findById(parentId)
                    .orElseThrow(() -> new AppException("NOT_FOUND", "上级目录不存在", HttpStatus.NOT_FOUND));
        }
        f.setParentId(parentId);
        return folderRepository.save(f);
    }

    @Transactional
    public void deleteFolder(Long id) {
        CaseFolder f = getFolder(id);
        if (folderRepository.countByParentId(id) > 0) {
            throw new AppException("INVALID", "请先删除或移走子目录", HttpStatus.BAD_REQUEST);
        }
        if (caseRepository.countActiveByFolderId(id) > 0) {
            throw new AppException("INVALID", "目录下仍有用例，请先移走或删除", HttpStatus.BAD_REQUEST);
        }
        folderRepository.delete(f);
    }

    @Transactional
    public void reorderFolders(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return;
        for (Map<String, Object> item : items) {
            if (item.get("id") == null) continue;
            Long id = Long.valueOf(item.get("id").toString());
            CaseFolder f = getFolder(id);
            if (item.containsKey("parent_id")) {
                Long parentId = item.get("parent_id") != null ? Long.valueOf(item.get("parent_id").toString()) : null;
                if (parentId != null && (parentId.equals(id) || isDescendant(id, parentId))) {
                    throw new AppException("INVALID", "目录移动位置无效", HttpStatus.BAD_REQUEST);
                }
                f.setParentId(parentId);
            }
            if (item.containsKey("sort_order") && item.get("sort_order") != null) {
                f.setSortOrder(Integer.valueOf(item.get("sort_order").toString()));
            }
            folderRepository.save(f);
        }
    }

    private CaseFolder getFolder(Long id) {
        CaseFolder f = folderRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "目录不存在", HttpStatus.NOT_FOUND));
        teamScope.assertTeamAccess(f.getTeamId());
        return f;
    }

    private int nextSortOrder(Long parentId) {
        List<CaseFolder> siblings = parentId == null
                ? folderRepository.findByParentIdIsNullOrderBySortOrderAsc()
                : folderRepository.findByParentIdOrderBySortOrderAsc(parentId);
        return siblings.stream()
                .map(CaseFolder::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private boolean isDescendant(Long ancestorId, Long nodeId) {
        if (nodeId == null) return false;
        Long current = nodeId;
        while (current != null) {
            if (current.equals(ancestorId)) return true;
            CaseFolder folder = folderRepository.findById(current).orElse(null);
            if (folder == null) break;
            current = folder.getParentId();
        }
        return false;
    }

    // --- Cases ---

    public Page<TestCase> listCases(int page, int pageSize, Long folderId, String status, String keyword) {
        TestCase.CaseStatus s = status != null && !status.isBlank() ? TestCase.CaseStatus.valueOf(status) : null;
        String kw = keyword != null && !keyword.isBlank() ? keyword : null;
        return caseRepository.findActive(folderId, s, kw, teamScope.scopeTeamId(), PageRequest.of(page - 1, pageSize));
    }

    public TestCase getCase(Long id) {
        TestCase c = caseRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用例不存在", HttpStatus.NOT_FOUND));
        if (c.getDeletedAt() != null) {
            throw new AppException("NOT_FOUND", "用例已删除", HttpStatus.NOT_FOUND);
        }
        teamScope.assertTeamAccess(c.getTeamId());
        return c;
    }

    @Transactional
    public TestCase createCase(Map<String, Object> body, Long userId) {
        TestCase c = mapToCase(new TestCase(), body);
        c.setOwnerId(userId);
        if (c.getCaseStatus() == null) {
            c.setCaseStatus(TestCase.CaseStatus.draft);
        }
        c.setVersionNum(1);
        Long reqTeam = body.get("team_id") != null ? Long.valueOf(body.get("team_id").toString()) : null;
        c.setTeamId(teamScope.teamIdForCreate(reqTeam));
        c = caseRepository.save(c);
        saveVersion(c, userId, "初始创建");
        return c;
    }

    @Transactional
    public TestCase updateCase(Long id, Map<String, Object> body, Long userId) {
        TestCase c = getCase(id);
        mapToCase(c, body);
        c.setVersionNum(c.getVersionNum() + 1);
        c = caseRepository.save(c);
        saveVersion(c, userId, body.getOrDefault("change_note", "编辑更新").toString());
        return c;
    }

    @Transactional
    public TestCase submitReview(Long id, Long userId) {
        TestCase c = getCase(id);
        if (c.getCaseStatus() != TestCase.CaseStatus.draft) {
            throw new AppException("INVALID", "仅草稿状态可提交评审", HttpStatus.BAD_REQUEST);
        }
        c.setCaseStatus(TestCase.CaseStatus.review);
        return caseRepository.save(c);
    }

    @Transactional
    public TestCase approveCase(Long id, Long userId) {
        TestCase c = getCase(id);
        if (c.getCaseStatus() != TestCase.CaseStatus.review) {
            throw new AppException("INVALID", "仅待评审用例可批准", HttpStatus.BAD_REQUEST);
        }
        c.setCaseStatus(TestCase.CaseStatus.active);
        c.setVersionNum(c.getVersionNum() + 1);
        c = caseRepository.save(c);
        saveVersion(c, userId, "评审通过");
        return c;
    }

    @Transactional
    public TestCase rejectCase(Long id, String reason, Long userId) {
        TestCase c = getCase(id);
        if (c.getCaseStatus() != TestCase.CaseStatus.review) {
            throw new AppException("INVALID", "仅待评审用例可驳回", HttpStatus.BAD_REQUEST);
        }
        c.setCaseStatus(TestCase.CaseStatus.draft);
        if (reason != null && !reason.isBlank()) {
            c.setPreconditions((c.getPreconditions() != null ? c.getPreconditions() + "\n" : "") + "[评审驳回] " + reason);
        }
        return caseRepository.save(c);
    }

    @Transactional
    public void deleteCase(Long id, Long userId) {
        TestCase c = getCase(id);
        c.setDeletedAt(LocalDateTime.now());
        caseRepository.save(c);
        recycleBinService.add("test_case", id, c.getName(), c, userId);
    }

    @Transactional
    public Map<String, Object> transferCases(List<Long> caseIds, Long ownerId) {
        if (ownerId == null) {
            throw new AppException("INVALID", "请指定新负责人", HttpStatus.BAD_REQUEST);
        }
        int count = 0;
        for (Long id : caseIds) {
            TestCase c = getCase(id);
            c.setOwnerId(ownerId);
            caseRepository.save(c);
            count++;
        }
        return Map.of("transferred", count, "owner_id", ownerId);
    }

    public List<TestCaseVersion> listVersions(Long caseId) {
        getCase(caseId);
        return versionRepository.findByCaseIdOrderByVersionNumDesc(caseId);
    }

    public Map<String, Object> compareVersions(Long caseId, Long versionIdA, Long versionIdB) {
        getCase(caseId);
        TestCaseVersion va = loadVersion(caseId, versionIdA);
        TestCaseVersion vb = loadVersion(caseId, versionIdB);
        try {
            TestCase snapA = objectMapper.readValue(va.getSnapshotJson(), TestCase.class);
            TestCase snapB = objectMapper.readValue(vb.getSnapshotJson(), TestCase.class);
            List<Map<String, Object>> stepsA = parseSteps(snapA.getStepsContent());
            List<Map<String, Object>> stepsB = parseSteps(snapB.getStepsContent());
            Map<String, Object> diff = diffSteps(stepsA, stepsB);
            diff.put("version_a", va.getVersionNum());
            diff.put("version_b", vb.getVersionNum());
            diff.put("version_a_id", va.getId());
            diff.put("version_b_id", vb.getId());
            diff.put("case_id", caseId);
            return diff;
        } catch (Exception e) {
            throw new AppException("INVALID", "版本对比失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private TestCaseVersion loadVersion(Long caseId, Long versionId) {
        TestCaseVersion ver = versionRepository.findById(versionId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "版本不存在", HttpStatus.NOT_FOUND));
        if (!ver.getCaseId().equals(caseId)) {
            throw new AppException("INVALID", "版本不匹配", HttpStatus.BAD_REQUEST);
        }
        return ver;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseSteps(String stepsContent) throws Exception {
        if (stepsContent == null || stepsContent.isBlank()) {
            return List.of();
        }
        var root = objectMapper.readTree(stepsContent);
        var stepsNode = root.has("steps") ? root.get("steps") : root;
        if (!stepsNode.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        for (var node : stepsNode) {
            steps.add(objectMapper.convertValue(node, Map.class));
        }
        return steps;
    }

    private Map<String, Object> diffSteps(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        int max = Math.max(a.size(), b.size());
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> modified = new ArrayList<>();
        List<Map<String, Object>> unchanged = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            Map<String, Object> sa = i < a.size() ? a.get(i) : null;
            Map<String, Object> sb = i < b.size() ? b.get(i) : null;
            if (sa == null && sb != null) {
                added.add(Map.of("index", i + 1, "step", sb));
            } else if (sa != null && sb == null) {
                removed.add(Map.of("index", i + 1, "step", sa));
            } else if (sa != null && stepEquals(sa, sb)) {
                unchanged.add(Map.of("index", i + 1, "step", sa));
            } else if (sa != null) {
                modified.add(Map.of("index", i + 1, "before", sa, "after", sb));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("steps_a_count", a.size());
        result.put("steps_b_count", b.size());
        result.put("added", added);
        result.put("removed", removed);
        result.put("modified", modified);
        result.put("unchanged_count", unchanged.size());
        result.put("has_diff", !added.isEmpty() || !removed.isEmpty() || !modified.isEmpty());
        return result;
    }

    private boolean stepEquals(Map<String, Object> a, Map<String, Object> b) {
        try {
            return objectMapper.writeValueAsString(a).equals(objectMapper.writeValueAsString(b));
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public TestCase rollback(Long caseId, Long versionId, Long userId) {
        TestCase c = getCase(caseId);
        TestCaseVersion ver = versionRepository.findById(versionId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "版本不存在", HttpStatus.NOT_FOUND));
        if (!ver.getCaseId().equals(caseId)) {
            throw new AppException("INVALID", "版本不匹配", HttpStatus.BAD_REQUEST);
        }
        try {
            TestCase snap = objectMapper.readValue(ver.getSnapshotJson(), TestCase.class);
            c.setStepsContent(snap.getStepsContent());
            c.setPreconditions(snap.getPreconditions());
            c.setExpectedResult(snap.getExpectedResult());
            c.setAppPackage(snap.getAppPackage());
            c.setPlatform(snap.getPlatform());
            c.setTags(snap.getTags());
            c.setVersionNum(c.getVersionNum() + 1);
            c = caseRepository.save(c);
            saveVersion(c, userId, "回滚至 v" + ver.getVersionNum());
            return c;
        } catch (Exception e) {
            throw new AppException("INVALID", "回滚失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public TestTask runCase(Long caseId, Long userId, List<Long> deviceIds) {
        return runCase(caseId, userId, deviceIds, null);
    }

    @Transactional
    public TestTask runCase(Long caseId, Long userId, List<Long> deviceIds, String appPackageOverride) {
        TestCase c = getCase(caseId);
        if (c.getCaseStatus() != TestCase.CaseStatus.active && c.getCaseStatus() != TestCase.CaseStatus.draft) {
            throw new AppException("INVALID", "仅草稿或生效用例可执行", HttpStatus.BAD_REQUEST);
        }
        CreateTaskRequest req = new CreateTaskRequest();
        req.setName("[用例] " + c.getName());
        req.setPlatform(c.getPlatform().name());
        req.setScriptType(c.getScriptType());
        req.setScriptContent(c.getStepsContent());
        String appPackage = c.getAppPackage();
        if (appPackageOverride != null && !appPackageOverride.isBlank()) {
            appPackage = appPackageOverride.trim();
        }
        req.setAppPackage(appPackage);
        req.setParallelCount(1);
        req.setTimeoutSeconds(c.getTimeoutSeconds());
        req.setEnableRecording(c.getEnableRecording());
        req.setPriority(c.getPriority());
        if (deviceIds != null && !deviceIds.isEmpty()) {
            req.setDeviceIds(deviceIds);
        }
        TestTask task = taskService.create(req, userId);
        task.setSourceCaseId(c.getId());
        task.setEnvId(c.getEnvId());
        task.setDatasetId(c.getDatasetId());
        task.setTeamId(c.getTeamId());
        task = taskRepository.save(task);
        taskService.submit(task.getId());
        schedulerService.enqueueTask(task.getId(), task.getPriority());
        return task;
    }

    public Map<String, Object> dependencyCheck(Long caseId) {
        long suiteRefs = suiteItemRepository.countByCaseId(caseId);
        return Map.of("suite_refs", suiteRefs);
    }

    private void saveVersion(TestCase c, Long userId, String note) {
        try {
            TestCaseVersion v = new TestCaseVersion();
            v.setCaseId(c.getId());
            v.setVersionNum(c.getVersionNum());
            v.setSnapshotJson(objectMapper.writeValueAsString(c));
            v.setChangeNote(note);
            v.setCreatedBy(userId);
            versionRepository.save(v);
        } catch (Exception ignored) {
        }
    }

    private TestCase mapToCase(TestCase c, Map<String, Object> body) {
        if (body.containsKey("name")) c.setName(body.get("name").toString());
        if (body.containsKey("folder_id")) {
            Object fid = body.get("folder_id");
            c.setFolderId(fid == null || "".equals(fid.toString()) ? null : Long.valueOf(fid.toString()));
        }
        if (body.containsKey("preconditions")) c.setPreconditions(str(body.get("preconditions")));
        if (body.containsKey("expected_result")) c.setExpectedResult(str(body.get("expected_result")));
        if (body.containsKey("steps_content")) c.setStepsContent(str(body.get("steps_content")));
        if (body.containsKey("script_type")) c.setScriptType(str(body.get("script_type")));
        if (body.containsKey("platform")) c.setPlatform(TestCase.Platform.valueOf(str(body.get("platform"))));
        if (body.containsKey("app_package")) c.setAppPackage(str(body.get("app_package")));
        if (body.containsKey("module_name")) c.setModuleName(str(body.get("module_name")));
        if (body.containsKey("priority") && body.get("priority") != null) {
            c.setPriority(parsePriority(body.get("priority")));
        }
        if (body.containsKey("tags")) c.setTags(str(body.get("tags")));
        if (body.containsKey("case_status") && body.get("case_status") != null
                && !body.get("case_status").toString().isBlank()) {
            c.setCaseStatus(TestCase.CaseStatus.valueOf(str(body.get("case_status"))));
        }
        if (body.containsKey("requirement_id")) c.setRequirementId(str(body.get("requirement_id")));
        if (body.containsKey("defect_id")) c.setDefectId(str(body.get("defect_id")));
        if (body.containsKey("env_id") && body.get("env_id") != null) {
            c.setEnvId(Long.valueOf(body.get("env_id").toString()));
        }
        if (body.containsKey("dataset_id") && body.get("dataset_id") != null) {
            c.setDatasetId(Long.valueOf(body.get("dataset_id").toString()));
        }
        if (body.containsKey("enable_recording")) c.setEnableRecording(Boolean.valueOf(body.get("enable_recording").toString()));
        if (body.containsKey("timeout_seconds") && body.get("timeout_seconds") != null) {
            c.setTimeoutSeconds(Integer.valueOf(body.get("timeout_seconds").toString()));
        }
        return c;
    }

    /** 兼容 P0/P1/数字/high-medium-low */
    private Byte parsePriority(Object raw) {
        String v = raw.toString().trim().toLowerCase();
        return switch (v) {
            case "0", "p0", "critical" -> (byte) 0;
            case "1", "p1", "high", "高" -> (byte) 1;
            case "2", "p2", "medium", "mid", "中" -> (byte) 2;
            case "3", "p3", "low", "低" -> (byte) 3;
            default -> {
                try {
                    yield Byte.valueOf(v);
                } catch (NumberFormatException e) {
                    yield (byte) 1;
                }
            }
        };
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
