package com.atp.platform.controller;

import com.atp.platform.common.ApiResponse;
import com.atp.platform.entity.FunctionTag;
import com.atp.platform.entity.MachineInfo;
import com.atp.platform.entity.MachineTagRel;
import com.atp.platform.security.SecurityUtils;
import com.atp.platform.service.MachineAdaptationDemoService;
import com.atp.platform.service.MachineAdaptationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 机型适配：机型档案、功能标签、关联与筛选（第一周后端）。
 */
@RestController
@RequestMapping("/api/v1/machine-adaptation")
@RequiredArgsConstructor
public class MachineAdaptationController {

    private final MachineAdaptationService service;
    private final MachineAdaptationDemoService demoService;

    // ─── 机型 ───────────────────────────────────────────────

    @GetMapping("/machines")
    public ApiResponse<List<MachineInfo>> listMachines() {
        return ApiResponse.ok(service.listMachines());
    }

    @GetMapping("/machines/{id}")
    public ApiResponse<Map<String, Object>> getMachine(@PathVariable Long id) {
        return ApiResponse.ok(service.getMachineDetail(id));
    }

    @PostMapping("/machines")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<MachineInfo> createMachine(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createMachine(body));
    }

    @PutMapping("/machines/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<MachineInfo> updateMachine(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateMachine(id, body));
    }

    @DeleteMapping("/machines/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> deleteMachine(@PathVariable Long id) {
        service.deleteMachine(id);
        return ApiResponse.ok();
    }

    @GetMapping("/machines/{id}/tags")
    public ApiResponse<List<Map<String, Object>>> listMachineTags(@PathVariable Long id) {
        return ApiResponse.ok(service.listMachineTagViews(id));
    }

    @PutMapping("/machines/{id}/tags")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<List<Map<String, Object>>> replaceMachineTags(
            @PathVariable Long id,
            @RequestBody List<Map<String, Object>> items) {
        return ApiResponse.ok(service.replaceMachineTags(id, items));
    }

    @PostMapping("/machines/{id}/tags")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<MachineTagRel> upsertMachineTag(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long tagId = Long.valueOf(body.get("tag_id").toString());
        Byte isSupport = body.get("is_support") == null
                ? (byte) 1
                : Byte.valueOf(body.get("is_support").toString());
        return ApiResponse.ok(service.upsertMachineTag(id, tagId, isSupport));
    }

    @DeleteMapping("/machines/{machineId}/tags/{tagId}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> removeMachineTag(@PathVariable Long machineId, @PathVariable Long tagId) {
        service.removeMachineTag(machineId, tagId);
        return ApiResponse.ok();
    }

    /** 按机型智能筛选用例 */
    @GetMapping("/machines/{id}/compatible-cases")
    public ApiResponse<Map<String, Object>> filterCases(
            @PathVariable Long id,
            @RequestParam(value = "match_mode", required = false, defaultValue = "all") String matchMode,
            @RequestParam(value = "include_untagged", required = false) Boolean includeUntagged) {
        return ApiResponse.ok(service.filterCasesByMachine(id, matchMode, includeUntagged));
    }

    // ─── 功能标签 ───────────────────────────────────────────

    /** App 功能集树：功能集 → 一级板块 → 板块内容 + 用例数 */
    @GetMapping("/feature-tree")
    public ApiResponse<Map<String, Object>> featureTree(
            @RequestParam(value = "machine_id", required = false) Long machineId) {
        return ApiResponse.ok(service.featureTree(machineId));
    }

    /** 按勾选功能集筛选用例 */
    @PostMapping("/cases/by-features")
    public ApiResponse<Map<String, Object>> casesByFeatures(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.get("tag_ids");
        List<Long> tagIds = raw == null ? List.of() : raw.stream()
                .map(v -> Long.valueOf(v.toString()))
                .toList();
        String matchMode = body.get("match_mode") == null ? "any" : body.get("match_mode").toString();
        return ApiResponse.ok(service.filterCasesByFeatures(tagIds, matchMode));
    }

    /**
     * 按勾选功能项拼装一条组合用例（已绑定公共步骤 → invoke_common）。
     * body: { tag_ids, name?, platform?, case_status?, module_name?, app_package? }
     */
    @PostMapping("/cases/compose")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> composeCase(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.composeCaseFromFeatures(body, SecurityUtils.currentUserId()));
    }

    @GetMapping("/tags")
    public ApiResponse<List<FunctionTag>> listTags(
            @RequestParam(value = "enabled_only", required = false) Boolean enabledOnly) {
        return ApiResponse.ok(service.listTags(enabledOnly));
    }

    @PostMapping("/tags")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<FunctionTag> createTag(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.createTag(body));
    }

    @PutMapping("/tags/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<FunctionTag> updateTag(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.updateTag(id, body));
    }

    @DeleteMapping("/tags/{id}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin')")
    public ApiResponse<Void> deleteTag(@PathVariable Long id) {
        service.deleteTag(id);
        return ApiResponse.ok();
    }

    // ─── 用例打标 ───────────────────────────────────────────

    @GetMapping("/cases/{caseId}/tags")
    public ApiResponse<List<Map<String, Object>>> listCaseTags(@PathVariable Long caseId) {
        return ApiResponse.ok(service.listCaseTags(caseId));
    }

    @PutMapping("/cases/{caseId}/tags")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<List<Map<String, Object>>> replaceCaseTags(
            @PathVariable Long caseId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.get("tag_ids");
        List<Long> tagIds = raw == null ? List.of() : raw.stream()
                .map(v -> Long.valueOf(v.toString()))
                .toList();
        return ApiResponse.ok(service.replaceCaseTags(caseId, tagIds));
    }

    @PostMapping("/cases/{caseId}/tags")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<?> addCaseTag(@PathVariable Long caseId, @RequestBody Map<String, Object> body) {
        Long tagId = Long.valueOf(body.get("tag_id").toString());
        return ApiResponse.ok(service.addCaseTag(caseId, tagId));
    }

    @DeleteMapping("/cases/{caseId}/tags/{tagId}")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Void> removeCaseTag(@PathVariable Long caseId, @PathVariable Long tagId) {
        service.removeCaseTag(caseId, tagId);
        return ApiResponse.ok();
    }

    // ─── 批量录入（Day5） ───────────────────────────────────

    /**
     * 一次性批量导入机型 / 标签 / 机型支持关系 / 用例打标。
     * body 示例：
     * {
     *   "machines":[{"machine_name":"AX17","hard_version":"HW1","firm_version":"1.0.0"}],
     *   "tags":[{"tag_name":"蓝牙配网","tag_type":"联网"}],
     *   "machine_tags":[{"machine_name":"AX17","tag_name":"蓝牙配网","is_support":1}],
     *   "case_tags":[{"case_id":1,"tag_name":"蓝牙配网"}]
     * }
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> batchImport(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.batchImport(body));
    }

    /** 一键加载机型/功能标签 Demo（不含执行结果） */
    @PostMapping("/load-demo")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> loadDemo() {
        return ApiResponse.ok(service.loadDemoPackage());
    }

    /** 一键加载完整测试结果 Demo（功能标签 + 用例 + 任务/日志/报告） */
    @PostMapping("/load-full-demo")
    @PreAuthorize("hasAnyRole('super_admin', 'test_admin', 'tester')")
    public ApiResponse<Map<String, Object>> loadFullDemo() {
        return ApiResponse.ok(demoService.loadFullResultDemo());
    }
}
