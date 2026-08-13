package com.atp.platform.service;

import com.atp.platform.entity.CaseTagRel;
import com.atp.platform.entity.CommonStep;
import com.atp.platform.entity.FunctionTag;
import com.atp.platform.entity.MachineInfo;
import com.atp.platform.entity.MachineTagRel;
import com.atp.platform.entity.TestCase;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.CaseTagRelRepository;
import com.atp.platform.repository.CommonStepRepository;
import com.atp.platform.repository.FunctionTagRepository;
import com.atp.platform.repository.MachineInfoRepository;
import com.atp.platform.repository.MachineTagRelRepository;
import com.atp.platform.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 机型适配筛选：机型档案、功能标签、关联与按机型筛选用例。
 */
@Service
@RequiredArgsConstructor
public class MachineAdaptationService {

    /** App 功能集固定排版顺序 */
    private static final List<String> FEATURE_SECTION_ORDER = List.of(
            "清洁功能", "地图功能", "智能功能", "设备管理", "通知体系", "用户体系");

    private final MachineInfoRepository machineRepository;
    private final FunctionTagRepository tagRepository;
    private final MachineTagRelRepository machineTagRelRepository;
    private final CaseTagRelRepository caseTagRelRepository;
    private final TestCaseRepository caseRepository;
    private final CommonStepRepository commonStepRepository;
    private final CaseCatalogService caseCatalogService;
    private final ObjectMapper objectMapper;
    private final TeamScopeService teamScope;

    // ─── 机型 ───────────────────────────────────────────────

    public List<MachineInfo> listMachines() {
        Long teamId = teamScope.scopeTeamId();
        if (teamId == null) {
            return machineRepository.findAllByOrderByIdAsc();
        }
        return machineRepository.findByTeamIdOrderByIdAsc(teamId);
    }

    public Map<String, Object> getMachineDetail(Long id) {
        MachineInfo m = requireMachine(id);
        Map<String, Object> row = toMachineMap(m);
        row.put("tags", listMachineTagViews(id));
        return row;
    }

    @Transactional
    public MachineInfo createMachine(Map<String, Object> body) {
        String name = requireText(body, "machine_name", "机型名称不能为空");
        Long teamId = teamScope.teamIdForCreate(asLong(body.get("team_id")));
        if (machineRepository.existsByMachineNameAndTeamId(name, teamId)) {
            throw new AppException("DUPLICATE", "机型名称已存在: " + name, HttpStatus.BAD_REQUEST);
        }
        MachineInfo m = new MachineInfo();
        m.setMachineName(name);
        m.setHardVersion(asText(body.get("hard_version")));
        m.setFirmVersion(asText(body.get("firm_version")));
        m.setRemark(asText(body.get("remark")));
        m.setStatus(asByte(body.get("status"), (byte) 1));
        m.setTeamId(teamId);
        return machineRepository.save(m);
    }

    @Transactional
    public MachineInfo updateMachine(Long id, Map<String, Object> body) {
        MachineInfo m = requireMachine(id);
        if (body.containsKey("machine_name")) {
            String name = requireText(body, "machine_name", "机型名称不能为空");
            if (machineRepository.existsByMachineNameAndTeamIdAndIdNot(name, m.getTeamId(), id)) {
                throw new AppException("DUPLICATE", "机型名称已存在: " + name, HttpStatus.BAD_REQUEST);
            }
            m.setMachineName(name);
        }
        if (body.containsKey("hard_version")) {
            m.setHardVersion(asText(body.get("hard_version")));
        }
        if (body.containsKey("firm_version")) {
            m.setFirmVersion(asText(body.get("firm_version")));
        }
        if (body.containsKey("remark")) {
            m.setRemark(asText(body.get("remark")));
        }
        if (body.containsKey("status")) {
            m.setStatus(asByte(body.get("status"), m.getStatus()));
        }
        return machineRepository.save(m);
    }

    @Transactional
    public void deleteMachine(Long id) {
        MachineInfo m = requireMachine(id);
        machineTagRelRepository.deleteByMachineId(id);
        machineRepository.delete(m);
    }

    // ─── 功能标签 ───────────────────────────────────────────

    public List<FunctionTag> listTags(Boolean enabledOnly) {
        Long teamId = teamScope.scopeTeamId();
        boolean onlyEnabled = Boolean.TRUE.equals(enabledOnly);
        if (teamId == null) {
            return onlyEnabled
                    ? tagRepository.findByStatusOrderByTagTypeAscTagNameAscContentNameAsc((byte) 1)
                    : tagRepository.findAllByOrderByTagTypeAscTagNameAscContentNameAsc();
        }
        return onlyEnabled
                ? tagRepository.findByTeamIdAndStatusOrderByTagTypeAscTagNameAscContentNameAsc(teamId, (byte) 1)
                : tagRepository.findByTeamIdOrderByTagTypeAscTagNameAscContentNameAsc(teamId);
    }

    @Transactional
    public FunctionTag createTag(Map<String, Object> body) {
        String board = requireText(body, "tag_name", "一级板块不能为空");
        String featureSet = requireText(body, "tag_type", "功能集不能为空");
        String content = requireText(body, "content_name", "板块内容不能为空");
        Long teamId = teamScope.teamIdForCreate(asLong(body.get("team_id")));
        if (tagRepository.existsByTagTypeAndTagNameAndContentNameAndTeamId(featureSet, board, content, teamId)) {
            throw new AppException("DUPLICATE",
                    "功能项已存在: " + featureSet + " / " + board + " / " + content, HttpStatus.BAD_REQUEST);
        }
        FunctionTag t = new FunctionTag();
        t.setTagName(board);
        t.setTagType(featureSet);
        t.setContentName(content);
        t.setDescription(asText(body.get("description")));
        t.setStatus(asByte(body.get("status"), (byte) 1));
        t.setTeamId(teamId);
        applyCommonStepBinding(t, body, true);
        return tagRepository.save(t);
    }

    @Transactional
    public FunctionTag updateTag(Long id, Map<String, Object> body) {
        FunctionTag t = requireTag(id);
        boolean touchHierarchy = body.containsKey("tag_name")
                || body.containsKey("tag_type")
                || body.containsKey("content_name");
        if (touchHierarchy) {
            String board = body.containsKey("tag_name")
                    ? requireText(body, "tag_name", "一级板块不能为空")
                    : t.getTagName();
            String featureSet = body.containsKey("tag_type")
                    ? requireText(body, "tag_type", "功能集不能为空")
                    : t.getTagType();
            String content = body.containsKey("content_name")
                    ? requireText(body, "content_name", "板块内容不能为空")
                    : (t.getContentName() == null || t.getContentName().isBlank()
                    ? "" : t.getContentName());
            if (content.isBlank()) {
                throw new AppException("INVALID", "板块内容不能为空", HttpStatus.BAD_REQUEST);
            }
            if (tagRepository.existsByTagTypeAndTagNameAndContentNameAndTeamIdAndIdNot(
                    featureSet, board, content, t.getTeamId(), id)) {
                throw new AppException("DUPLICATE",
                        "功能项已存在: " + featureSet + " / " + board + " / " + content, HttpStatus.BAD_REQUEST);
            }
            t.setTagName(board);
            t.setTagType(featureSet);
            t.setContentName(content);
        }
        if (body.containsKey("description")) {
            t.setDescription(asText(body.get("description")));
        }
        if (body.containsKey("status")) {
            t.setStatus(asByte(body.get("status"), t.getStatus()));
        }
        applyCommonStepBinding(t, body, false);
        return tagRepository.save(t);
    }

    @Transactional
    public void deleteTag(Long id) {
        FunctionTag t = requireTag(id);
        machineTagRelRepository.deleteByTagId(id);
        caseTagRelRepository.deleteByTagId(id);
        tagRepository.delete(t);
    }

    // ─── 机型-标签关系 ──────────────────────────────────────

    public List<Map<String, Object>> listMachineTagViews(Long machineId) {
        requireMachine(machineId);
        List<MachineTagRel> rels = machineTagRelRepository.findByMachineId(machineId);
        Map<Long, FunctionTag> tags = loadTags(rels.stream().map(MachineTagRel::getTagId).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (MachineTagRel rel : rels) {
            FunctionTag tag = tags.get(rel.getTagId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rel.getId());
            row.put("machine_id", rel.getMachineId());
            row.put("tag_id", rel.getTagId());
            row.put("is_support", rel.getIsSupport());
            row.put("tag_name", tag != null ? tag.getTagName() : null);
            row.put("tag_type", tag != null ? tag.getTagType() : null);
            row.put("content_name", tag != null ? tag.getContentName() : null);
            out.add(row);
        }
        return out;
    }

    @Transactional
    public List<Map<String, Object>> replaceMachineTags(Long machineId, List<Map<String, Object>> items) {
        requireMachine(machineId);
        machineTagRelRepository.deleteByMachineId(machineId);
        // 先落库删除，避免同事务内再 insert 触发 uk_machine_tag 冲突
        machineTagRelRepository.flush();
        if (items != null) {
            Set<Long> seen = new HashSet<>();
            for (Map<String, Object> item : items) {
                Long tagId = asLong(item.get("tag_id"));
                if (tagId == null || !seen.add(tagId)) {
                    continue;
                }
                requireTag(tagId);
                MachineTagRel rel = new MachineTagRel();
                rel.setMachineId(machineId);
                rel.setTagId(tagId);
                rel.setIsSupport(asByte(item.get("is_support"), (byte) 1));
                machineTagRelRepository.save(rel);
            }
        }
        return listMachineTagViews(machineId);
    }

    @Transactional
    public MachineTagRel upsertMachineTag(Long machineId, Long tagId, Byte isSupport) {
        requireMachine(machineId);
        requireTag(tagId);
        MachineTagRel rel = machineTagRelRepository.findByMachineIdAndTagId(machineId, tagId)
                .orElseGet(MachineTagRel::new);
        rel.setMachineId(machineId);
        rel.setTagId(tagId);
        rel.setIsSupport(isSupport != null ? isSupport : (byte) 1);
        return machineTagRelRepository.save(rel);
    }

    @Transactional
    public void removeMachineTag(Long machineId, Long tagId) {
        requireMachine(machineId);
        machineTagRelRepository.deleteByMachineIdAndTagId(machineId, tagId);
    }

    // ─── 用例-标签关系 ──────────────────────────────────────

    public List<Map<String, Object>> listCaseTags(Long caseId) {
        requireCase(caseId);
        List<CaseTagRel> rels = caseTagRelRepository.findByCaseId(caseId);
        Map<Long, FunctionTag> tags = loadTags(rels.stream().map(CaseTagRel::getTagId).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (CaseTagRel rel : rels) {
            FunctionTag tag = tags.get(rel.getTagId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rel.getId());
            row.put("case_id", rel.getCaseId());
            row.put("tag_id", rel.getTagId());
            row.put("tag_name", tag != null ? tag.getTagName() : null);
            row.put("tag_type", tag != null ? tag.getTagType() : null);
            row.put("content_name", tag != null ? tag.getContentName() : null);
            out.add(row);
        }
        return out;
    }

    @Transactional
    public List<Map<String, Object>> replaceCaseTags(Long caseId, List<Long> tagIds) {
        requireCase(caseId);
        caseTagRelRepository.deleteByCaseId(caseId);
        caseTagRelRepository.flush();
        if (tagIds != null) {
            Set<Long> seen = new HashSet<>();
            for (Long tagId : tagIds) {
                if (tagId == null || !seen.add(tagId)) {
                    continue;
                }
                requireTag(tagId);
                CaseTagRel rel = new CaseTagRel();
                rel.setCaseId(caseId);
                rel.setTagId(tagId);
                caseTagRelRepository.save(rel);
            }
        }
        return listCaseTags(caseId);
    }

    @Transactional
    public CaseTagRel addCaseTag(Long caseId, Long tagId) {
        requireCase(caseId);
        requireTag(tagId);
        return caseTagRelRepository.findByCaseIdAndTagId(caseId, tagId).orElseGet(() -> {
            CaseTagRel rel = new CaseTagRel();
            rel.setCaseId(caseId);
            rel.setTagId(tagId);
            return caseTagRelRepository.save(rel);
        });
    }

    @Transactional
    public void removeCaseTag(Long caseId, Long tagId) {
        requireCase(caseId);
        caseTagRelRepository.deleteByCaseIdAndTagId(caseId, tagId);
    }

    // ─── 智能筛选 ───────────────────────────────────────────

    /**
     * App 功能集树：功能集(tag_type) → 一级板块(tag_name) → 板块内容(content_name)，附带用例数。
     * 可选 machineId：标记该机型是否支持该项（用于新品适配预勾选）。
     */
    public Map<String, Object> featureTree(Long machineId) {
        List<FunctionTag> tags = listTags(true);
        Set<Long> supported = Set.of();
        if (machineId != null) {
            requireMachine(machineId);
            supported = machineTagRelRepository.findByMachineIdAndIsSupport(machineId, (byte) 1)
                    .stream().map(MachineTagRel::getTagId).collect(Collectors.toSet());
        }

        Map<Long, Long> caseCountByTag = new HashMap<>();
        for (CaseTagRel rel : caseTagRelRepository.findAll()) {
            caseCountByTag.merge(rel.getTagId(), 1L, Long::sum);
        }

        Map<String, List<FunctionTag>> grouped = new LinkedHashMap<>();
        for (String name : FEATURE_SECTION_ORDER) {
            grouped.put(name, new ArrayList<>());
        }
        for (FunctionTag t : tags) {
            String section = (t.getTagType() == null || t.getTagType().isBlank()) ? "未分类" : t.getTagType().trim();
            grouped.computeIfAbsent(section, k -> new ArrayList<>()).add(t);
        }

        // 固定顺序：标准六项在前，其余（如未分类）追加在后
        List<String> sectionOrder = new ArrayList<>(FEATURE_SECTION_ORDER);
        for (String key : grouped.keySet()) {
            if (!sectionOrder.contains(key)) {
                sectionOrder.add(key);
            }
        }

        List<Map<String, Object>> sections = new ArrayList<>();
        long totalCases = 0;
        for (String sectionName : sectionOrder) {
            List<FunctionTag> sectionTags = grouped.getOrDefault(sectionName, List.of());
            Map<String, List<FunctionTag>> byBoard = new LinkedHashMap<>();
            for (FunctionTag t : sectionTags) {
                String board = (t.getTagName() == null || t.getTagName().isBlank()) ? "未分组" : t.getTagName();
                byBoard.computeIfAbsent(board, k -> new ArrayList<>()).add(t);
            }

            // 一级板块按首次添加时间正序（后添加的在下面）
            List<Map.Entry<String, List<FunctionTag>>> boardEntries = new ArrayList<>(byBoard.entrySet());
            boardEntries.sort(Comparator.comparingLong(e -> earliestTagId(e.getValue())));

            List<Map<String, Object>> boards = new ArrayList<>();
            List<Map<String, Object>> flatItems = new ArrayList<>();
            long sectionCount = 0;
            for (Map.Entry<String, List<FunctionTag>> be : boardEntries) {
                List<FunctionTag> boardTags = new ArrayList<>(be.getValue());
                boardTags.sort(Comparator.comparingLong(t -> t.getId() == null ? Long.MAX_VALUE : t.getId()));
                List<Map<String, Object>> items = new ArrayList<>();
                long boardCount = 0;
                for (FunctionTag t : boardTags) {
                    long cnt = caseCountByTag.getOrDefault(t.getId(), 0L);
                    boardCount += cnt;
                    Map<String, Object> item = toFeatureItem(t, cnt, machineId, supported);
                    items.add(item);
                    flatItems.add(item);
                }
                sectionCount += boardCount;
                Map<String, Object> board = new LinkedHashMap<>();
                board.put("board_name", be.getKey());
                board.put("case_count", boardCount);
                board.put("item_count", items.size());
                board.put("items", items);
                boards.add(board);
            }
            totalCases += sectionCount;
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("section_name", sectionName);
            section.put("case_count", sectionCount);
            section.put("item_count", flatItems.size());
            section.put("boards", boards);
            section.put("items", flatItems);
            sections.add(section);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("machine_id", machineId);
        result.put("section_count", sections.size());
        result.put("tag_count", tags.size());
        result.put("total_case_links", totalCases);
        result.put("sections", sections);
        return result;
    }

    private Map<String, Object> toFeatureItem(FunctionTag t, long cnt, Long machineId, Set<Long> supported) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tag_id", t.getId());
        item.put("tag_name", t.getTagName());
        item.put("tag_type", t.getTagType());
        item.put("content_name", t.getContentName());
        item.put("case_count", cnt);
        item.put("supported", machineId == null ? null : supported.contains(t.getId()));
        item.put("common_step_id", t.getCommonStepId());
        CommonStep bound = resolveActiveCommonStep(t.getCommonStepId());
        item.put("common_step_name", bound != null ? bound.getName() : null);
        item.put("composable", bound != null);
        return item;
    }

    /**
     * 按勾选功能项顺序，将已绑定公共步骤拼装为一条 visual 用例并落库。
     * 未绑定 / 步骤失效的功能项写入 unbound，不阻断已绑定项的组合。
     */
    @Transactional
    public Map<String, Object> composeCaseFromFeatures(Map<String, Object> body, Long userId) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.get("tag_ids");
        List<Long> tagIds = raw == null ? List.of() : raw.stream()
                .filter(Objects::nonNull)
                .map(v -> Long.valueOf(v.toString()))
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
        if (tagIds.isEmpty()) {
            throw new AppException("INVALID", "请先勾选功能项", HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        List<Map<String, Object>> unbound = new ArrayList<>();
        List<Long> usedTagIds = new ArrayList<>();
        Set<String> seenStepNames = new LinkedHashSet<>();

        for (Long tagId : tagIds) {
            FunctionTag tag = requireTag(tagId);
            Long stepId = tag.getCommonStepId();
            if (stepId == null) {
                unbound.add(unboundItem(tag, "未绑定公共步骤"));
                continue;
            }
            CommonStep step = resolveActiveCommonStep(stepId);
            if (step == null) {
                unbound.add(unboundItem(tag, "绑定的公共步骤不存在或已停用"));
                continue;
            }
            if (!seenStepNames.add(step.getName())) {
                continue;
            }
            Map<String, Object> stepNode = new LinkedHashMap<>();
            stepNode.put("type", "invoke_common");
            stepNode.put("common_step", step.getName());
            stepNode.put("name", featureDisplayName(tag));
            stepNode.put("remark", featurePath(tag));
            steps.add(stepNode);
            usedTagIds.add(tag.getId());
        }

        if (steps.isEmpty()) {
            throw new AppException("INVALID",
                    "勾选的功能项均未绑定可用公共步骤，请先在「功能集管理」中绑定",
                    HttpStatus.BAD_REQUEST);
        }

        String caseName = asText(body.get("name"));
        if (caseName == null || caseName.isBlank()) {
            caseName = "新品适配组合 · "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        }

        Map<String, Object> visual = new LinkedHashMap<>();
        visual.put("version", 1);
        visual.put("steps", steps);

        String stepsJson;
        try {
            stepsJson = objectMapper.writeValueAsString(visual);
        } catch (Exception e) {
            throw new AppException("INVALID", "生成步骤失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> caseBody = new LinkedHashMap<>();
        caseBody.put("name", caseName);
        caseBody.put("script_type", "visual");
        caseBody.put("steps_content", stepsJson);
        caseBody.put("module_name", asText(body.get("module_name")) != null
                ? asText(body.get("module_name")) : "新品适配");
        caseBody.put("case_status", body.get("case_status") != null
                ? body.get("case_status").toString() : "draft");
        caseBody.put("platform", body.get("platform") != null
                ? body.get("platform").toString() : "android");
        caseBody.put("preconditions", "由机型适配「功能集勾选」自动组合生成（invoke_common）");
        caseBody.put("tags", "新品适配,组合用例");
        if (body.get("app_package") != null) {
            caseBody.put("app_package", body.get("app_package").toString());
        }

        TestCase created = caseCatalogService.createCase(caseBody, userId);
        replaceCaseTags(created.getId(), usedTagIds);

        Map<Long, FunctionTag> tagCache = loadTags(usedTagIds);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("case", toCaseBrief(created, new LinkedHashSet<>(usedTagIds), tagCache));
        result.put("step_count", steps.size());
        result.put("bound_tag_count", usedTagIds.size());
        result.put("unbound", unbound);
        result.put("steps", steps);
        return result;
    }

    private void applyCommonStepBinding(FunctionTag tag, Map<String, Object> body, boolean creating) {
        if (!creating && !body.containsKey("common_step_id") && !body.containsKey("common_step_name")) {
            return;
        }
        Long stepId = null;
        if (body.containsKey("common_step_id")) {
            Object raw = body.get("common_step_id");
            if (raw != null && !raw.toString().isBlank()) {
                stepId = Long.valueOf(raw.toString());
            }
        } else if (body.containsKey("common_step_name")) {
            String name = asText(body.get("common_step_name"));
            if (name != null && !name.isBlank()) {
                CommonStep step = commonStepRepository.findByNameAndDeletedAtIsNull(name)
                        .orElseThrow(() -> new AppException("NOT_FOUND",
                                "公共步骤不存在: " + name, HttpStatus.BAD_REQUEST));
                stepId = step.getId();
            }
        }
        if (stepId != null && resolveActiveCommonStep(stepId) == null) {
            throw new AppException("INVALID", "公共步骤不存在或已停用", HttpStatus.BAD_REQUEST);
        }
        tag.setCommonStepId(stepId);
    }

    private CommonStep resolveActiveCommonStep(Long stepId) {
        if (stepId == null) {
            return null;
        }
        return commonStepRepository.findById(stepId)
                .filter(s -> s.getDeletedAt() == null)
                .filter(s -> s.getStatus() == null || s.getStatus() == CommonStep.StepStatus.active)
                .orElse(null);
    }

    private static Map<String, Object> unboundItem(FunctionTag tag, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tag_id", tag.getId());
        row.put("tag_type", tag.getTagType());
        row.put("tag_name", tag.getTagName());
        row.put("content_name", tag.getContentName());
        row.put("common_step_id", tag.getCommonStepId());
        row.put("reason", reason);
        return row;
    }

    private static String featureDisplayName(FunctionTag tag) {
        String content = tag.getContentName();
        if (content != null && !content.isBlank()) {
            return content.trim();
        }
        return tag.getTagName();
    }

    private static String featurePath(FunctionTag tag) {
        return String.join(" / ",
                nullToEmpty(tag.getTagType()),
                nullToEmpty(tag.getTagName()),
                nullToEmpty(tag.getContentName()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /** 一级板块排序键：该板块下最早添加标签的 id（越小越靠前） */
    private static long earliestTagId(List<FunctionTag> tags) {
        long min = Long.MAX_VALUE;
        for (FunctionTag t : tags) {
            if (t.getId() != null && t.getId() < min) {
                min = t.getId();
            }
        }
        return min;
    }

    /**
     * 按勾选的功能集筛选用例（新品适配勾选测试）。
     * match_mode=any（默认）：用例命中任一勾选功能即可；
     * match_mode=all：用例依赖功能必须全部在勾选集合内。
     */
    public Map<String, Object> filterCasesByFeatures(List<Long> tagIds, String matchMode) {
        Set<Long> selected = tagIds == null ? Set.of() : tagIds.stream()
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        for (Long id : selected) {
            requireTag(id);
        }

        Long scopeTeamId = teamScope.scopeTeamId();
        List<TestCase> cases = caseRepository.findByDeletedAtIsNull().stream()
                .filter(c -> scopeTeamId == null || Objects.equals(scopeTeamId, c.getTeamId()))
                .toList();

        Map<Long, Set<Long>> caseTagMap = new HashMap<>();
        List<Long> caseIds = cases.stream().map(TestCase::getId).toList();
        if (!caseIds.isEmpty()) {
            for (CaseTagRel rel : caseTagRelRepository.findByCaseIdIn(caseIds)) {
                caseTagMap.computeIfAbsent(rel.getCaseId(), k -> new HashSet<>()).add(rel.getTagId());
            }
        }

        boolean anyMode = matchMode == null || matchMode.isBlank() || "any".equalsIgnoreCase(matchMode);
        Map<Long, FunctionTag> tagCache = loadTags(selected);
        List<Map<String, Object>> matched = new ArrayList<>();

        if (!selected.isEmpty()) {
            for (TestCase c : cases) {
                Set<Long> tags = caseTagMap.getOrDefault(c.getId(), Set.of());
                if (tags.isEmpty()) {
                    continue;
                }
                boolean ok = anyMode
                        ? tags.stream().anyMatch(selected::contains)
                        : selected.containsAll(tags);
                if (ok) {
                    matched.add(toCaseBrief(c, tags, tagCache));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tag_ids", new ArrayList<>(selected));
        result.put("match_mode", anyMode ? "any" : "all");
        result.put("matched_count", matched.size());
        result.put("cases", matched);
        return result;
    }

    /**
     * 按机型筛选用例。
     * match_mode=all（默认）：用例全部标签均被机型支持；
     * match_mode=any：用例至少有一个标签被机型支持；
     * include_untagged：是否包含未打标用例（默认 true）。
     */
    public Map<String, Object> filterCasesByMachine(Long machineId, String matchMode, Boolean includeUntagged) {
        MachineInfo machine = requireMachine(machineId);
        Set<Long> supportedTagIds = machineTagRelRepository.findByMachineIdAndIsSupport(machineId, (byte) 1)
                .stream().map(MachineTagRel::getTagId).collect(Collectors.toSet());

        Long scopeTeamId = teamScope.scopeTeamId();
        List<TestCase> cases = caseRepository.findByDeletedAtIsNull().stream()
                .filter(c -> {
                    if (scopeTeamId != null) {
                        return Objects.equals(scopeTeamId, c.getTeamId());
                    }
                    if (machine.getTeamId() != null) {
                        return c.getTeamId() == null || Objects.equals(machine.getTeamId(), c.getTeamId());
                    }
                    return true;
                })
                .toList();

        Map<Long, Set<Long>> caseTagMap = new HashMap<>();
        List<Long> caseIds = cases.stream().map(TestCase::getId).toList();
        if (!caseIds.isEmpty()) {
            for (CaseTagRel rel : caseTagRelRepository.findByCaseIdIn(caseIds)) {
                caseTagMap.computeIfAbsent(rel.getCaseId(), k -> new HashSet<>()).add(rel.getTagId());
            }
        }

        boolean anyMode = "any".equalsIgnoreCase(matchMode);
        boolean keepUntagged = includeUntagged == null || includeUntagged;

        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> unmatched = new ArrayList<>();
        Map<Long, FunctionTag> tagCache = loadTags(supportedTagIds);

        for (TestCase c : cases) {
            Set<Long> tags = caseTagMap.getOrDefault(c.getId(), Set.of());
            boolean ok;
            if (tags.isEmpty()) {
                ok = keepUntagged;
            } else if (anyMode) {
                ok = tags.stream().anyMatch(supportedTagIds::contains);
            } else {
                ok = supportedTagIds.containsAll(tags);
            }
            Map<String, Object> row = toCaseBrief(c, tags, tagCache);
            if (ok) {
                matched.add(row);
            } else {
                unmatched.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("machine", toMachineMap(machine));
        result.put("supported_tag_ids", new ArrayList<>(supportedTagIds));
        result.put("supported_tags", supportedTagIds.stream().map(id -> {
            FunctionTag t = tagCache.get(id);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tag_id", id);
            row.put("tag_name", t != null ? t.getTagName() : null);
            row.put("tag_type", t != null ? t.getTagType() : null);
            row.put("content_name", t != null ? t.getContentName() : null);
            return row;
        }).toList());
        result.put("match_mode", anyMode ? "any" : "all");
        result.put("include_untagged", keepUntagged);
        result.put("matched_count", matched.size());
        result.put("unmatched_count", unmatched.size());
        result.put("cases", matched);
        result.put("unmatched_cases", unmatched);
        return result;
    }

    // ─── 批量录入 ───────────────────────────────────────────

    @Transactional
    public Map<String, Object> batchImport(Map<String, Object> body) {
        int machineCreated = 0, machineUpdated = 0;
        int tagCreated = 0, tagUpdated = 0;
        int machineTagUpserted = 0;
        int caseTagUpserted = 0;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> machines = (List<Map<String, Object>>) body.get("machines");
        if (machines != null) {
            for (Map<String, Object> item : machines) {
                String name = requireText(item, "machine_name", "批量机型缺少 machine_name");
                Long teamId = teamScope.teamIdForCreate(asLong(item.get("team_id")));
                MachineInfo existing = firstOrNull(machineRepository.findByMachineNameAndTeamId(name, teamId));
                if (existing == null) {
                    createMachine(item);
                    machineCreated++;
                } else {
                    updateMachine(existing.getId(), item);
                    machineUpdated++;
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) body.get("tags");
        if (tags != null) {
            for (Map<String, Object> item : tags) {
                requireText(item, "tag_name", "批量标签缺少 tag_name");
                requireText(item, "tag_type", "批量标签缺少 tag_type");
                requireText(item, "content_name", "批量标签缺少 content_name");
                Long teamId = teamScope.teamIdForCreate(asLong(item.get("team_id")));
                FunctionTag existing = firstOrNull(tagRepository.findByTagTypeAndTagNameAndContentNameAndTeamId(
                        asText(item.get("tag_type")),
                        asText(item.get("tag_name")),
                        asText(item.get("content_name")),
                        teamId));
                if (existing == null) {
                    createTag(item);
                    tagCreated++;
                } else {
                    updateTag(existing.getId(), item);
                    tagUpdated++;
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> machineTags = (List<Map<String, Object>>) body.get("machine_tags");
        if (machineTags != null) {
            for (Map<String, Object> item : machineTags) {
                Long machineId = resolveMachineId(item);
                Long tagId = resolveTagId(item);
                Byte support = asByte(item.get("is_support"), (byte) 1);
                upsertMachineTag(machineId, tagId, support);
                machineTagUpserted++;
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> caseTags = (List<Map<String, Object>>) body.get("case_tags");
        if (caseTags != null) {
            for (Map<String, Object> item : caseTags) {
                Long caseId = asLong(item.get("case_id"));
                if (caseId == null) {
                    throw new AppException("INVALID", "批量用例打标缺少 case_id", HttpStatus.BAD_REQUEST);
                }
                Long tagId = resolveTagId(item);
                addCaseTag(caseId, tagId);
                caseTagUpserted++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("machine_created", machineCreated);
        summary.put("machine_updated", machineUpdated);
        summary.put("tag_created", tagCreated);
        summary.put("tag_updated", tagUpdated);
        summary.put("machine_tag_upserted", machineTagUpserted);
        summary.put("case_tag_upserted", caseTagUpserted);
        return summary;
    }

    /**
     * 加载机型适配测试 Demo（classpath: samples/machine-adaptation-demo.json）。
     * 幂等：已存在的机型/功能项会更新，缺失的会新增。
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadDemoPackage() {
        try (java.io.InputStream in = getClass().getClassLoader()
                .getResourceAsStream("samples/machine-adaptation-demo.json")) {
            if (in == null) {
                throw new AppException("NOT_FOUND", "未找到 Demo 样例文件", HttpStatus.NOT_FOUND);
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> body = mapper.readValue(in, Map.class);
            Map<String, Object> summary = batchImport(body);
            summary.put("source", "samples/machine-adaptation-demo.json");
            summary.put("hint", "可在任务页选择机型 CX2 / X30 / AX17 体验勾选与设置类型分组");
            return summary;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("INVALID", "加载 Demo 失败: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    // ─── helpers ────────────────────────────────────────────

    private Long resolveMachineId(Map<String, Object> item) {
        Long id = asLong(item.get("machine_id"));
        if (id != null) {
            requireMachine(id);
            return id;
        }
        String name = asText(item.get("machine_name"));
        if (name == null || name.isBlank()) {
            throw new AppException("INVALID", "需要 machine_id 或 machine_name", HttpStatus.BAD_REQUEST);
        }
        Long teamId = teamScope.teamIdForCreate(asLong(item.get("team_id")));
        MachineInfo m = firstOrNull(machineRepository.findByMachineNameAndTeamId(name, teamId));
        if (m == null) {
            throw new AppException("NOT_FOUND", "机型不存在: " + name, HttpStatus.NOT_FOUND);
        }
        return m.getId();
    }

    private Long resolveTagId(Map<String, Object> item) {
        Long id = asLong(item.get("tag_id"));
        if (id != null) {
            requireTag(id);
            return id;
        }
        Long teamId = teamScope.teamIdForCreate(asLong(item.get("team_id")));
        String featureSet = asText(item.get("tag_type"));
        String board = asText(item.get("tag_name"));
        String content = asText(item.get("content_name"));
        if (content != null && !content.isBlank()
                && board != null && !board.isBlank()
                && featureSet != null && !featureSet.isBlank()) {
            FunctionTag t = firstPreferEnabled(tagRepository.findByTagTypeAndTagNameAndContentNameAndTeamId(
                    featureSet, board, content, teamId));
            if (t == null) {
                throw new AppException("NOT_FOUND",
                        "标签不存在: " + featureSet + "/" + board + "/" + content, HttpStatus.NOT_FOUND);
            }
            return t.getId();
        }
        if (content != null && !content.isBlank()) {
            FunctionTag t = firstPreferEnabled(tagRepository.findByContentNameAndTeamIdOrderByIdAsc(content, teamId));
            if (t == null) {
                throw new AppException("NOT_FOUND", "标签不存在: " + content, HttpStatus.NOT_FOUND);
            }
            return t.getId();
        }
        if (board == null || board.isBlank()) {
            throw new AppException("INVALID", "需要 tag_id、content_name 或 tag_name", HttpStatus.BAD_REQUEST);
        }
        FunctionTag t = firstPreferEnabled(tagRepository.findByTagNameAndTeamIdOrderByIdAsc(board, teamId));
        if (t == null) {
            throw new AppException("NOT_FOUND", "标签不存在: " + board, HttpStatus.NOT_FOUND);
        }
        return t.getId();
    }

    private static <T> T firstOrNull(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    /** 有重复时优先取启用项，否则取第一条 */
    private static FunctionTag firstPreferEnabled(List<FunctionTag> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (FunctionTag t : list) {
            if (t.getStatus() != null && t.getStatus() == 1) {
                return t;
            }
        }
        return list.get(0);
    }

    private MachineInfo requireMachine(Long id) {
        MachineInfo m = machineRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "机型不存在", HttpStatus.NOT_FOUND));
        teamScope.assertTeamAccess(m.getTeamId());
        return m;
    }

    private FunctionTag requireTag(Long id) {
        FunctionTag t = tagRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "功能标签不存在", HttpStatus.NOT_FOUND));
        teamScope.assertTeamAccess(t.getTeamId());
        return t;
    }

    private TestCase requireCase(Long id) {
        TestCase c = caseRepository.findById(id)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> new AppException("NOT_FOUND", "用例不存在", HttpStatus.NOT_FOUND));
        teamScope.assertTeamAccess(c.getTeamId());
        return c;
    }

    private Map<Long, FunctionTag> loadTags(Collection<Long> ids) {
        Map<Long, FunctionTag> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (FunctionTag t : tagRepository.findAllById(ids)) {
            map.put(t.getId(), t);
        }
        return map;
    }

    private Map<String, Object> toMachineMap(MachineInfo m) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("machine_name", m.getMachineName());
        row.put("hard_version", m.getHardVersion());
        row.put("firm_version", m.getFirmVersion());
        row.put("status", m.getStatus());
        row.put("team_id", m.getTeamId());
        row.put("remark", m.getRemark());
        row.put("created_at", m.getCreatedAt());
        row.put("updated_at", m.getUpdatedAt());
        return row;
    }

    private Map<String, Object> toCaseBrief(TestCase c, Set<Long> tagIds, Map<Long, FunctionTag> tagCache) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("name", c.getName());
        row.put("module_name", c.getModuleName());
        row.put("case_status", c.getCaseStatus());
        row.put("folder_id", c.getFolderId());
        row.put("team_id", c.getTeamId());
        row.put("tag_ids", new ArrayList<>(tagIds));
        row.put("tags", tagIds.stream().map(id -> {
            FunctionTag t = tagCache.get(id);
            if (t == null) {
                t = tagRepository.findById(id).orElse(null);
                if (t != null) {
                    tagCache.put(id, t);
                }
            }
            Map<String, Object> tag = new LinkedHashMap<>();
            tag.put("tag_id", id);
            tag.put("tag_name", t != null ? t.getTagName() : null);
            tag.put("tag_type", t != null ? t.getTagType() : null);
            tag.put("content_name", t != null ? t.getContentName() : null);
            return tag;
        }).toList());
        return row;
    }

    private String requireText(Map<String, Object> body, String key, String message) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new AppException("INVALID", message, HttpStatus.BAD_REQUEST);
        }
        return v.toString().trim();
    }

    private String asText(Object v) {
        return v == null ? null : v.toString().trim();
    }

    private Long asLong(Object v) {
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return Long.valueOf(v.toString());
    }

    private Byte asByte(Object v, Byte defaultValue) {
        if (v == null || v.toString().isBlank()) {
            return defaultValue;
        }
        if (v instanceof Boolean b) {
            return b ? (byte) 1 : (byte) 0;
        }
        return Byte.valueOf(v.toString());
    }
}
