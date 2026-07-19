package com.atp.platform.service;

import com.atp.platform.dto.ControlPoolLookupContext;
import com.atp.platform.dto.CreateControlPoolRequest;
import com.atp.platform.entity.*;
import com.atp.platform.exception.AppException;
import com.atp.platform.exception.ErrorCodes;
import com.atp.platform.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import com.atp.platform.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ControlPoolService {

    private final ControlPoolRepository poolRepository;
    private final PrivateControlBindingRepository bindingRepository;
    private final ControlChangeLogRepository changeLogRepository;
    private final CaseHealingRecordRepository healingRepository;
    private final TestCaseRepository caseRepository;
    private final CommonStepRepository commonStepRepository;
    private final ControlPoolVersionRepository versionRepository;
    private final TestTaskRepository taskRepository;
    private final TestEnvironmentRepository environmentRepository;
    private final AppPackageRepository appPackageRepository;
    private final TestSuiteRepository suiteRepository;
    private final TestSuiteItemRepository suiteItemRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern ELEMENT_NAME_JSON = Pattern.compile("\"element_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ELEMENT_NAME_PY = Pattern.compile("(?:get_locator|tap_element|find)\\([\"']([^\"']+)[\"']");

    private static final Pattern ELEMENT_NAME_PATTERN = Pattern.compile(
            "^[\\u4e00-\\u9fff][\\u4e00-\\u9fffA-Za-z0-9_\\-]{0,47}$|^[a-z][a-z0-9_]{2,47}$");

    public record ResolvedControl(String key, String elementName, String locatorType,
                                  String locatorValue, String source, Integer stepIndex,
                                  Map<String, Object> locators, List<Map<String, Object>> locatorChain,
                                  Map<String, Object> waitRule) {
        public ResolvedControl(String key, String elementName, String locatorType,
                               String locatorValue, String source, Integer stepIndex) {
            this(key, elementName, locatorType, locatorValue, source, stepIndex, null, null, null);
        }

        public ResolvedControl(String key, String elementName, String locatorType,
                               String locatorValue, String source, Integer stepIndex,
                               Map<String, Object> locators, List<Map<String, Object>> locatorChain) {
            this(key, elementName, locatorType, locatorValue, source, stepIndex, locators, locatorChain, null);
        }
    }

    public ControlPool create(CreateControlPoolRequest req, Long operatorId) {
        validateElementName(req.getElementName());
        if (req.getLocatorValue() == null || req.getLocatorValue().isBlank()) {
            throw new AppException(ErrorCodes.E4003, HttpStatus.BAD_REQUEST);
        }
        String poolKey = generatePoolKey(req.getAppPackage(), req.getPageName(), req.getElementName(),
                req.getTeamId(), req.getPlatform(), req.getVersionTag(), req.getEnvTag());
        if (poolRepository.findByPoolKey(poolKey).isPresent()) {
            throw new AppException("DUPLICATE", "控件池条目已存在", HttpStatus.CONFLICT);
        }
        ControlPool pool = new ControlPool();
        pool.setPoolKey(poolKey);
        pool.setAppPackage(req.getAppPackage());
        pool.setPageName(req.getPageName());
        pool.setElementName(req.getElementName());
        pool.setTeamId(req.getTeamId());
        if (req.getPlatform() != null) {
            pool.setPlatform(Device.Platform.valueOf(req.getPlatform()));
        }
        pool.setLocatorType(req.getLocatorType());
        pool.setLocatorValue(req.getLocatorValue());
        pool.setVersionTag(normalizeDim(req.getVersionTag()));
        pool.setEnvTag(normalizeDim(req.getEnvTag()));
        pool.setIsCore(Boolean.TRUE.equals(req.getIsCore()));
        pool.setControlTag(req.getControlTag() != null && !req.getControlTag().isBlank() ? req.getControlTag() : "static");
        pool.setFeatureVector(buildFeatureVectorJson(req));
        pool.setStatus("active");
        pool = poolRepository.save(pool);
        logChange(pool.getId(), null, ControlChangeLog.ChangeType.create, null,
                snapshotPool(pool), operatorId, "新建控件池条目");
        archiveVersion(pool, "初始创建", operatorId, null, null, null);
        return pool;
    }

    public ControlPool getPool(Long id) {
        return poolRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCodes.E4001, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Map<String, Object> deletePool(Long id, boolean force) {
        ControlPool pool = getPool(id);
        assertCanEditPool(pool);
        Map<String, Object> deps = scanDependencies(id);
        int total = ((Number) deps.get("total_refs")).intValue();
        if (total > 0 && !force) {
            throw new AppException("HAS_DEPS",
                    "控件仍被 " + total + " 处引用，请先处理依赖或确认强制删除",
                    HttpStatus.CONFLICT);
        }
        String name = pool.getElementName();
        bindingRepository.deleteByPoolId(id);
        versionRepository.deleteByPoolId(id);
        changeLogRepository.deleteByPoolId(id);
        poolRepository.delete(pool);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted_id", id);
        result.put("element_name", name);
        result.put("forced", force);
        result.put("cleared_refs", total);
        return result;
    }

    @Transactional
    public ControlPool updatePool(Long id, Map<String, Object> body, boolean propagateBindings, Long operatorId) {
        ControlPool pool = getPool(id);
        assertCanEditPool(pool);
        String before = snapshotPool(pool);
        if (body.containsKey("locator_type") && body.get("locator_type") != null) {
            pool.setLocatorType(body.get("locator_type").toString());
        }
        if (body.containsKey("locator_value") && body.get("locator_value") != null) {
            pool.setLocatorValue(body.get("locator_value").toString());
        }
        if (body.containsKey("page_name")) pool.setPageName(strVal(body.get("page_name")));
        if (body.containsKey("status") && body.get("status") != null) {
            pool.setStatus(body.get("status").toString());
        }
        if (body.containsKey("version_tag") && body.get("version_tag") != null) {
            pool.setVersionTag(normalizeDim(body.get("version_tag").toString()));
        }
        if (body.containsKey("env_tag") && body.get("env_tag") != null) {
            pool.setEnvTag(normalizeDim(body.get("env_tag").toString()));
        }
        if (body.containsKey("team_id") && body.get("team_id") != null) {
            pool.setTeamId(((Number) body.get("team_id")).longValue());
        }
        if (body.containsKey("is_core")) {
            pool.setIsCore(Boolean.parseBoolean(String.valueOf(body.get("is_core"))));
        }
        if (body.containsKey("control_tag") && body.get("control_tag") != null) {
            pool.setControlTag(body.get("control_tag").toString());
        }
        if (body.containsKey("locators") || body.containsKey("locator_chain")
                || body.containsKey("validate_result") || body.containsKey("validated_at")
                || body.containsKey("wait_rule")) {
            pool.setFeatureVector(mergeFeatureVector(pool.getFeatureVector(), body));
        }
        pool = poolRepository.save(pool);
        logChange(pool.getId(), null, ControlChangeLog.ChangeType.update, before,
                snapshotPool(pool), operatorId, body.getOrDefault("reason", "更新控件池").toString());
        if (propagateBindings) {
            for (PrivateControlBinding b : bindingRepository.findByPoolId(id)) {
                b.setLocatorType(pool.getLocatorType());
                b.setLocatorValue(pool.getLocatorValue());
                bindingRepository.save(b);
            }
        }
        archiveVersion(pool, body.getOrDefault("reason", "更新控件池").toString(), operatorId,
                strVal(body.get("env_tag")), strVal(body.get("requirement_id")), strVal(body.get("screenshot_path")));
        return pool;
    }

    public List<ControlPoolVersion> listVersions(Long poolId) {
        getPool(poolId);
        return versionRepository.findByPoolIdOrderByVersionNumDesc(poolId);
    }

    @Transactional
    public ControlPool rollbackToVersion(Long poolId, Long versionId, boolean propagateBindings, Long operatorId) {
        ControlPool pool = getPool(poolId);
        ControlPoolVersion ver = versionRepository.findById(versionId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "版本不存在", HttpStatus.NOT_FOUND));
        if (!ver.getPoolId().equals(poolId)) {
            throw new AppException("INVALID", "版本与控件不匹配", HttpStatus.BAD_REQUEST);
        }
        String before = snapshotPool(pool);
        try {
            JsonNode snap = objectMapper.readTree(ver.getSnapshotJson());
            if (snap.has("locator_type")) pool.setLocatorType(snap.get("locator_type").asText());
            if (snap.has("locator_value")) pool.setLocatorValue(snap.get("locator_value").asText());
            if (snap.has("page_name")) pool.setPageName(snap.get("page_name").asText());
            if (snap.has("status")) pool.setStatus(snap.get("status").asText());
            if (snap.has("element_name")) pool.setElementName(snap.get("element_name").asText());
        } catch (Exception e) {
            throw new AppException("INVALID", "版本快照解析失败", HttpStatus.BAD_REQUEST);
        }
        pool = poolRepository.save(pool);
        logChange(pool.getId(), null, ControlChangeLog.ChangeType.rollback, before,
                snapshotPool(pool), operatorId, "回滚至版本 v" + ver.getVersionNum());
        if (propagateBindings) {
            for (PrivateControlBinding b : bindingRepository.findByPoolId(poolId)) {
                b.setLocatorType(pool.getLocatorType());
                b.setLocatorValue(pool.getLocatorValue());
                bindingRepository.save(b);
            }
        }
        archiveVersion(pool, "回滚自 v" + ver.getVersionNum(), operatorId, ver.getEnvTag(), ver.getRequirementId(), ver.getScreenshotPath());
        return pool;
    }

    private void archiveVersion(ControlPool pool, String reason, Long operatorId,
                                String envTag, String requirementId, String screenshotPath) {
        int nextVer = versionRepository.findTopByPoolIdOrderByVersionNumDesc(pool.getId())
                .map(v -> v.getVersionNum() + 1).orElse(1);
        ControlPoolVersion v = new ControlPoolVersion();
        v.setPoolId(pool.getId());
        v.setVersionNum(nextVer);
        v.setEnvTag(envTag != null && !envTag.isBlank() ? envTag : "test");
        v.setRequirementId(requirementId);
        v.setScreenshotPath(screenshotPath);
        v.setSnapshotJson(snapshotPoolExtended(pool));
        v.setChangeReason(reason);
        v.setOperatorId(operatorId);
        versionRepository.save(v);
        pool.setVersionTag("v" + nextVer);
        poolRepository.save(pool);
    }

    /** 变更前依赖溯源：扫描用例/公共步骤/私有绑定 */
    public Map<String, Object> scanDependencies(Long poolId) {
        ControlPool pool = getPool(poolId);
        List<Map<String, Object>> caseRefs = new ArrayList<>();
        List<Map<String, Object>> commonRefs = new ArrayList<>();
        for (TestCase c : caseRepository.findByDeletedAtIsNull()) {
            scanStepsForElement(c.getStepsContent(), pool, "case", c.getId(), c.getName(), caseRefs);
        }
        for (CommonStep s : commonStepRepository.findByDeletedAtIsNullOrderByNameAsc()) {
            scanStepsForElement(s.getStepsContent(), pool, "common_step", s.getId(), s.getName(), commonRefs);
        }
        List<Map<String, Object>> bindingRefs = bindingRepository.findByPoolId(poolId).stream()
                .map(b -> Map.<String, Object>of(
                        "ref_type", "private_binding",
                        "ref_id", b.getId(),
                        "ref_name", "task#" + b.getTaskId() + " step#" + b.getStepIndex(),
                        "element_name", b.getElementName(),
                        "step_index", b.getStepIndex() != null ? b.getStepIndex() : -1
                ))
                .toList();
        List<Map<String, Object>> suiteRefs = new ArrayList<>();
        for (TestSuite suite : suiteRepository.findByDeletedAtIsNullOrderByUpdatedAtDesc()) {
            scanStepsForElement(suite.getHookBefore(), pool, "suite_hook", suite.getId(),
                    suite.getName() + " / before", suiteRefs);
            scanStepsForElement(suite.getHookAfter(), pool, "suite_hook", suite.getId(),
                    suite.getName() + " / after", suiteRefs);
            for (TestSuiteItem item : suiteItemRepository.findBySuiteIdOrderBySortOrderAsc(suite.getId())) {
                if (Boolean.FALSE.equals(item.getEnabled())) continue;
                caseRepository.findById(item.getCaseId()).ifPresent(c ->
                        scanStepsForElement(c.getStepsContent(), pool, "suite", suite.getId(),
                                suite.getName() + " / " + c.getName(), suiteRefs));
            }
        }
        int total = caseRefs.size() + commonRefs.size() + bindingRefs.size() + suiteRefs.size();
        return Map.of(
                "pool_id", poolId,
                "element_name", pool.getElementName(),
                "app_package", pool.getAppPackage() != null ? pool.getAppPackage() : "",
                "case_refs", caseRefs,
                "common_step_refs", commonRefs,
                "private_binding_refs", bindingRefs,
                "suite_refs", suiteRefs,
                "total_refs", total,
                "impact_level", total == 0 ? "none" : (total <= 3 ? "low" : (total <= 10 ? "medium" : "high"))
        );
    }

    /** 保留旧签名兼容 */
    public Page<ControlPool> list(int page, int pageSize, String appPackage) {
        return list(page, pageSize, appPackage, null, null, null, null, null, null);
    }

    public Page<ControlPool> list(int page, int pageSize, String appPackage, String pageName,
                                  String platform, String versionTag, String envTag, Long teamId, String status) {
        PageRequest pageable = PageRequest.of(page - 1, pageSize);
        return poolRepository.search(
                blankToNull(appPackage),
                blankToNull(pageName),
                blankToNull(platform),
                blankToNull(versionTag),
                blankToNull(envTag),
                teamId,
                blankToNull(status),
                pageable);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public Optional<ControlPool> lookup(String appPackage, String elementName) {
        return lookup(appPackage, elementName, ControlPoolLookupContext.empty());
    }

    public Optional<ControlPool> lookup(String appPackage, String elementName, ControlPoolLookupContext ctx) {
        List<ControlPool> candidates = poolRepository.findByAppPackageAndElementNameAndStatus(
                appPackage, elementName, "active");
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        ControlPool best = pickBestPool(candidates, ctx != null ? ctx : ControlPoolLookupContext.empty());
        if (best == null) {
            return Optional.empty();
        }
        best.setHitCount(best.getHitCount() + 1);
        poolRepository.save(best);
        return Optional.of(best);
    }

    public ControlPoolLookupContext contextFromTask(TestTask task) {
        if (task == null) {
            return ControlPoolLookupContext.empty();
        }
        String versionTag = "*";
        if (task.getAppPackageId() != null) {
            versionTag = appPackageRepository.findById(task.getAppPackageId())
                    .map(AppPackage::getVersionName)
                    .filter(v -> v != null && !v.isBlank())
                    .orElse("*");
        }
        String envTag = "*";
        if (task.getEnvId() != null) {
            envTag = environmentRepository.findById(task.getEnvId())
                    .map(TestEnvironment::getName)
                    .filter(v -> v != null && !v.isBlank())
                    .orElse("*");
        }
        String platform = task.getPlatform() != null ? task.getPlatform().name() : "both";
        return new ControlPoolLookupContext(task.getTeamId(), platform, versionTag, envTag);
    }

    public ControlPoolLookupContext contextFromTaskId(Long taskId) {
        if (taskId == null) {
            return ControlPoolLookupContext.empty();
        }
        return taskRepository.findById(taskId).map(this::contextFromTask).orElse(ControlPoolLookupContext.empty());
    }

    @Transactional
    public Map<String, Object> recordValidationOutcome(ControlPool pool, Map<String, Object> vr, Long operatorId) {
        Map<String, Object> fv = parseFeatureVector(pool);
        boolean valid = Boolean.TRUE.equals(vr.get("valid"));
        int prevStreak = fv.get("fail_streak") instanceof Number n ? n.intValue() : 0;
        int failStreak = valid ? 0 : prevStreak + 1;
        String validatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Map<String, Object> validateResult = new LinkedHashMap<>();
        validateResult.put("valid", valid);
        validateResult.put("matched_by", vr.getOrDefault("matched_by", ""));
        validateResult.put("error", vr.getOrDefault("error", ""));
        validateResult.put("validated_at", validatedAt);
        validateResult.put("fail_streak", failStreak);
        fv.put("validate_result", validateResult);
        fv.put("last_validation", validateResult);
        fv.put("validated_at", validatedAt);
        fv.put("fail_streak", failStreak);
        try {
            pool.setFeatureVector(objectMapper.writeValueAsString(fv));
        } catch (Exception ignored) {
        }
        boolean suggestArchive = failStreak >= ControlGovernanceService.FAIL_STREAK_ARCHIVE_THRESHOLD;
        boolean archived = false;
        if (suggestArchive && !"archived".equals(pool.getStatus())) {
            String beforeSnap = snapshotPool(pool);
            pool.setStatus("archived");
            archived = true;
            logChange(pool.getId(), null, ControlChangeLog.ChangeType.update, beforeSnap,
                    snapshotPool(pool), operatorId, "连续 " + failStreak + " 次校验失败，自动归档");
        }
        poolRepository.save(pool);
        return Map.of(
                "fail_streak", failStreak,
                "suggest_archive", suggestArchive,
                "archived", archived
        );
    }

    public List<String> collectElementNamesFromTask(TestTask task) {
        Set<String> names = new LinkedHashSet<>();
        if (task == null) return List.of();
        String content = task.getScriptContent();
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Matcher m1 = ELEMENT_NAME_JSON.matcher(content);
        while (m1.find()) {
            if (!m1.group(1).isBlank()) names.add(m1.group(1));
        }
        Matcher m2 = ELEMENT_NAME_PY.matcher(content);
        while (m2.find()) {
            if (!m2.group(1).isBlank()) names.add(m2.group(1));
        }
        return new ArrayList<>(names);
    }

    private ControlPool pickBestPool(List<ControlPool> pools, ControlPoolLookupContext ctx) {
        ControlPool best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ControlPool p : pools) {
            int score = scorePoolMatch(p, ctx);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private int scorePoolMatch(ControlPool p, ControlPoolLookupContext ctx) {
        int score = 0;
        if (ctx.teamId() != null) {
            if (ctx.teamId().equals(p.getTeamId())) score += 16;
            else if (p.getTeamId() == null) score += 4;
        } else if (p.getTeamId() == null) {
            score += 8;
        }
        if (dimensionMatches(p.getVersionTag(), ctx.versionTag())) score += 12;
        else score -= 4;
        if (dimensionMatches(p.getEnvTag(), ctx.envTag())) score += 12;
        else score -= 4;
        if (platformMatches(p.getPlatform(), ctx.platform())) score += 6;
        score += Math.min(p.getHitCount() != null ? p.getHitCount() : 0, 50);
        return score;
    }

    private boolean dimensionMatches(String poolVal, String ctxVal) {
        String p = normalizeDim(poolVal);
        String c = normalizeDim(ctxVal);
        if (p.equals(c)) return true;
        return "*".equals(p) || "*".equals(c);
    }

    private boolean platformMatches(Device.Platform poolPlatform, String ctxPlatform) {
        if (poolPlatform == null || ctxPlatform == null || ctxPlatform.isBlank() || "both".equals(ctxPlatform)) {
            return true;
        }
        return poolPlatform.name().equalsIgnoreCase(ctxPlatform);
    }

    private String normalizeDim(String val) {
        if (val == null || val.isBlank()) return "*";
        return val.trim();
    }

    public void validateElementName(String elementName) {
        if (elementName == null || elementName.isBlank()) {
            throw new AppException("INVALID", "请填写控件名称", HttpStatus.BAD_REQUEST);
        }
        String n = elementName.trim();
        boolean hasChinese = n.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
        boolean legacySnake = n.matches("^[a-z][a-z0-9_]{2,47}$");
        if (hasChinese) {
            if (!ELEMENT_NAME_PATTERN.matcher(n).matches()) {
                throw new AppException("INVALID", "控件名称格式不正确（建议 1-48 字，以中文开头）", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (!legacySnake) {
            throw new AppException("INVALID", "请使用中文标识控件用途", HttpStatus.BAD_REQUEST);
        }
    }

    private void assertCanEditPool(ControlPool pool) {
        if (!isProtectedPool(pool)) return;
        if (!SecurityUtils.hasAnyRole("super_admin", "test_admin")) {
            throw new AppException("FORBIDDEN", "生产环境核心控件仅管理员可修改", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isProtectedPool(ControlPool pool) {
        if (!Boolean.TRUE.equals(pool.getIsCore())) return false;
        String env = pool.getEnvTag() != null ? pool.getEnvTag().toLowerCase() : "";
        return env.contains("prod") || env.contains("production") || env.contains("gray");
    }

    public Page<ControlChangeLog> changeLogs(Long poolId, int page, int pageSize) {
        return changeLogRepository.findByPoolIdOrderByCreatedAtDesc(poolId, PageRequest.of(page - 1, pageSize));
    }

    private void scanStepsForElement(String stepsJson, ControlPool pool, String refType,
                                     Long refId, String refName, List<Map<String, Object>> out) {
        if (stepsJson == null || stepsJson.isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(stepsJson);
            JsonNode steps = root.isArray() ? root : root.path("steps");
            if (!steps.isArray()) return;
            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                String en = step.path("element_name").asText("");
                if (!en.isBlank() && en.equals(pool.getElementName())) {
                    out.add(Map.of(
                            "ref_type", refType,
                            "ref_id", refId,
                            "ref_name", refName,
                            "step_index", i,
                            "step_type", step.path("type").asText(""),
                            "element_name", en
                    ));
                }
            }
        } catch (Exception ignored) {
            String needle = "\"element_name\":\"" + pool.getElementName() + "\"";
            if (stepsJson.contains(needle)) {
                out.add(Map.of(
                        "ref_type", refType,
                        "ref_id", refId,
                        "ref_name", refName,
                        "step_index", -1,
                        "step_type", "unknown",
                        "element_name", pool.getElementName()
                ));
            }
        }
    }

    /**
     * 解析优先级：步骤私有绑定 → 任务级私有绑定 → 全局控件池
     */
    public ResolvedControl resolveLocator(Long taskId, Integer stepIndex, String elementName, String appPackage) {
        ControlPoolLookupContext ctx = contextFromTaskId(taskId);
        if (taskId != null && stepIndex != null) {
            Optional<PrivateControlBinding> stepBinding = bindingRepository
                    .findByTaskIdAndStepIndexAndElementNameAndIsIsolated(taskId, stepIndex, elementName, true);
            if (stepBinding.isPresent()) {
                return fromBinding(stepBinding.get(), stepIndex + "." + elementName);
            }
        }
        if (taskId != null) {
            for (PrivateControlBinding b : bindingRepository.findByTaskIdOrderByStepIndexAsc(taskId)) {
                if (elementName.equals(b.getElementName()) && Boolean.TRUE.equals(b.getIsIsolated())) {
                    String key = b.getStepIndex() != null ? b.getStepIndex() + "." + b.getElementName() : b.getElementName();
                    return fromBinding(b, key);
                }
            }
        }
        if (appPackage != null && !appPackage.isBlank()) {
            Optional<ControlPool> exact = lookup(appPackage, elementName, ctx);
            if (exact.isPresent()) {
                ControlPool p = exact.get();
                return fromPool(p, elementName, elementName, "pool", stepIndex);
            }
            Optional<ControlPool> fuzzy = fuzzyLookup(appPackage, elementName, ctx);
            if (fuzzy.isPresent()) {
                ControlPool p = fuzzy.get();
                return fromPool(p, elementName, p.getElementName(), "pool_fuzzy", stepIndex);
            }
            throw new AppException(ErrorCodes.E4001, HttpStatus.NOT_FOUND);
        }
        throw new AppException(ErrorCodes.E4002, HttpStatus.NOT_FOUND);
    }

    private Optional<ControlPool> fuzzyLookup(String appPackage, String elementName, ControlPoolLookupContext ctx) {
        if (elementName == null || elementName.isBlank()) return Optional.empty();
        List<ControlPool> pools = poolRepository.findByAppPackageAndStatus(appPackage, "active");
        ControlPool best = null;
        double bestScore = 0.0;
        for (ControlPool p : pools) {
            double sim = nameSimilarity(elementName, p.getElementName());
            if (sim < 0.72) continue;
            int dimScore = scorePoolMatch(p, ctx);
            double combined = sim * 100 + dimScore;
            if (combined > bestScore) {
                bestScore = combined;
                best = p;
            }
        }
        if (best != null) {
            best.setHitCount(best.getHitCount() + 1);
            poolRepository.save(best);
        }
        return Optional.ofNullable(best);
    }

    private static double nameSimilarity(String a, String b) {
        if (a == null || b == null) return 0;
        String x = a.toLowerCase().trim();
        String y = b.toLowerCase().trim();
        if (x.equals(y)) return 1.0;
        if (x.contains(y) || y.contains(x)) return 0.85;
        int dist = levenshtein(x, y);
        int maxLen = Math.max(x.length(), y.length());
        return maxLen == 0 ? 0 : 1.0 - (double) dist / maxLen;
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    @Transactional
    public Map<String, Object> batchReplaceElementName(String appPackage, String oldName, String newName,
                                                       boolean updateCases, Long operatorId) {
        if (oldName == null || newName == null || oldName.isBlank() || newName.isBlank()) {
            throw new AppException("INVALID", "old_name/new_name 必填", HttpStatus.BAD_REQUEST);
        }
        int poolUpdated = 0;
        for (ControlPool p : poolRepository.findByAppPackageAndStatus(appPackage, "active")) {
            if (oldName.equals(p.getElementName())) {
                p.setElementName(newName);
                poolRepository.save(p);
                poolUpdated++;
            }
        }
        int casesUpdated = 0;
        if (updateCases) {
            for (TestCase c : caseRepository.findByDeletedAtIsNull()) {
                if (c.getStepsContent() == null || !c.getStepsContent().contains(oldName)) continue;
                String updated = c.getStepsContent()
                        .replace("\"element_name\":\"" + oldName + "\"", "\"element_name\":\"" + newName + "\"")
                        .replace("\"element_name\": \"" + oldName + "\"", "\"element_name\": \"" + newName + "\"");
                if (!updated.equals(c.getStepsContent())) {
                    c.setStepsContent(updated);
                    caseRepository.save(c);
                    casesUpdated++;
                }
            }
        }
        return Map.of("pool_updated", poolUpdated, "cases_updated", casesUpdated);
    }

    /** 解析任务全部私有绑定 + 脚本引用控件池，供执行器批量注入 */
    public List<ResolvedControl> resolveAllForTask(TestTask task) {
        List<ResolvedControl> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        ControlPoolLookupContext ctx = contextFromTask(task);
        List<PrivateControlBinding> bindings = bindingRepository.findByTaskIdOrderByStepIndexAsc(task.getId());
        for (PrivateControlBinding b : bindings) {
            ResolvedControl rc = fromBinding(b, bindingKey(b));
            if (seen.add(rc.key())) {
                result.add(rc);
            }
            if (seen.add(b.getElementName())) {
                result.add(new ResolvedControl(b.getElementName(), b.getElementName(),
                        rc.locatorType(), rc.locatorValue(), rc.source(), b.getStepIndex(),
                        rc.locators(), rc.locatorChain()));
            }
        }
        String appPackage = task.getAppPackage();
        if (appPackage != null && !appPackage.isBlank()) {
            for (String elementName : collectElementNamesFromTask(task)) {
                if (!seen.add(elementName)) continue;
                lookup(appPackage, elementName, ctx).ifPresent(pool -> result.add(
                        fromPool(pool, elementName, elementName, "pool_script", null)));
            }
        }
        return result;
    }

    /**
     * 执行时自动采集控件批量入库（跳过已存在 poolKey）
     */
    @Transactional
    public int ingestAutoCollected(String appPackage, List<Map<String, Object>> items, Long operatorId) {
        if (appPackage == null || appPackage.isBlank() || items == null || items.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (Map<String, Object> item : items) {
            String elementName = strVal(item.get("element_name"));
            String locatorType = strVal(item.get("locator_type"));
            String locatorValue = strVal(item.get("locator_value"));
            String pageName = strVal(item.get("page_name"));
            if (elementName.isBlank() || locatorValue.isBlank()) {
                continue;
            }
            String poolKey = generatePoolKey(appPackage, pageName, elementName);
            if (poolRepository.findByPoolKey(poolKey).isPresent()) {
                continue;
            }
            ControlPool pool = new ControlPool();
            pool.setPoolKey(poolKey);
            pool.setAppPackage(appPackage);
            pool.setPageName(pageName);
            pool.setElementName(elementName);
            pool.setPlatform(Device.Platform.android);
            pool.setLocatorType(locatorType.isBlank() ? "id" : locatorType);
            pool.setLocatorValue(locatorValue);
            pool.setVersionTag("auto_collect");
            pool.setStatus("active");
            pool = poolRepository.save(pool);
            logChange(pool.getId(), null, ControlChangeLog.ChangeType.create, null,
                    snapshotPool(pool), operatorId, "执行时自动采集");
            added++;
        }
        return added;
    }

    private String strVal(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    public PrivateControlBinding createPrivateBinding(Long taskId, Integer stepIndex, String elementName,
                                                      String locatorType, String locatorValue,
                                                      Long poolId, Long createdBy) {
        if (locatorValue == null || locatorValue.isBlank()) {
            if (poolId == null) {
                throw new AppException(ErrorCodes.E4003, HttpStatus.BAD_REQUEST);
            }
            ControlPool pool = poolRepository.findById(poolId)
                    .orElseThrow(() -> new AppException(ErrorCodes.E4001, HttpStatus.NOT_FOUND));
            locatorType = pool.getLocatorType();
            locatorValue = pool.getLocatorValue();
        }
        PrivateControlBinding binding = new PrivateControlBinding();
        binding.setTaskId(taskId);
        binding.setStepIndex(stepIndex);
        binding.setElementName(elementName);
        binding.setLocatorType(locatorType != null ? locatorType : "id");
        binding.setLocatorValue(locatorValue);
        binding.setPoolId(poolId);
        binding.setIsIsolated(true);
        binding.setCreatedBy(createdBy);
        binding = bindingRepository.save(binding);
        logChange(null, binding.getId(), ControlChangeLog.ChangeType.create, null,
                snapshotBinding(binding), createdBy, "创建私有控件绑定");
        return binding;
    }

    public List<PrivateControlBinding> getPrivateBindings(Long taskId) {
        return bindingRepository.findByTaskIdOrderByStepIndexAsc(taskId);
    }

    @Transactional
    public void deletePrivateBinding(Long id, Long operatorId) {
        PrivateControlBinding binding = bindingRepository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "绑定不存在", HttpStatus.NOT_FOUND));
        logChange(null, id, ControlChangeLog.ChangeType.deprecate, snapshotBinding(binding),
                null, operatorId, "删除私有控件绑定");
        bindingRepository.delete(binding);
    }

    public CaseHealingRecord recordHealing(Long taskId, Long executionId, Integer stepIndex,
                                           String originalLocator, String healedLocator,
                                           CaseHealingRecord.HealStrategy strategy, boolean success) {
        CaseHealingRecord record = new CaseHealingRecord();
        record.setTaskId(taskId);
        record.setExecutionId(executionId);
        record.setStepIndex(stepIndex);
        record.setOriginalLocator(originalLocator);
        record.setHealedLocator(healedLocator);
        record.setHealStrategy(strategy);
        record.setSuccess(success);
        return healingRepository.save(record);
    }

    /** 执行失败时尝试 pool 降级自愈 */
    public Optional<ResolvedControl> tryPoolFallback(TestTask task, String elementName, Long executionId) {
        if (task.getAppPackage() == null || task.getAppPackage().isBlank()) {
            return Optional.empty();
        }
        Optional<ControlPool> pool = lookup(task.getAppPackage(), elementName, contextFromTask(task));
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        ControlPool p = pool.get();
        recordHealing(task.getId(), executionId, null, elementName, p.getLocatorValue(),
                CaseHealingRecord.HealStrategy.pool_fallback, true);
        return Optional.of(fromPool(p, elementName, elementName, "pool_fallback", null));
    }

    public List<CaseHealingRecord> getHealingRecords(Long taskId) {
        return healingRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    private ResolvedControl fromBinding(PrivateControlBinding b, String key) {
        if ("pool".equals(b.getLocatorType()) && b.getPoolId() != null) {
            ControlPool pool = poolRepository.findById(b.getPoolId())
                    .orElseThrow(() -> new AppException(ErrorCodes.E4001, HttpStatus.NOT_FOUND));
            return fromPool(pool, key, b.getElementName(), "pool_ref", b.getStepIndex());
        }
        return new ResolvedControl(key, b.getElementName(), b.getLocatorType(),
                b.getLocatorValue(), "private", b.getStepIndex());
    }

    private String bindingKey(PrivateControlBinding b) {
        return b.getStepIndex() != null ? b.getStepIndex() + "." + b.getElementName() : b.getElementName();
    }

    private void logChange(Long poolId, Long bindingId, ControlChangeLog.ChangeType type,
                           String before, String after, Long operatorId, String reason) {
        ControlChangeLog log = new ControlChangeLog();
        log.setPoolId(poolId);
        log.setBindingId(bindingId);
        log.setChangeType(type);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        changeLogRepository.save(log);
    }

    private String snapshotPool(ControlPool p) {
        try {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("element_name", p.getElementName());
            snap.put("locator_type", p.getLocatorType());
            snap.put("locator_value", p.getLocatorValue());
            snap.put("version_tag", p.getVersionTag() != null ? p.getVersionTag() : "");
            snap.put("env_tag", p.getEnvTag() != null ? p.getEnvTag() : "");
            snap.put("team_id", p.getTeamId());
            snap.put("status", p.getStatus());
            Map<String, Object> fv = parseFeatureVector(p);
            if (fv.get("locator_chain") != null) snap.put("locator_chain", fv.get("locator_chain"));
            if (fv.get("locators") != null) snap.put("locators", fv.get("locators"));
            if (fv.get("risk_level") != null) snap.put("risk_level", fv.get("risk_level"));
            if (fv.get("risk_tags") != null) snap.put("risk_tags", fv.get("risk_tags"));
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildFeatureVectorJson(CreateControlPoolRequest req) {
        try {
            Map<String, Object> fv = new LinkedHashMap<>();
            if (req.getDisplayName() != null && !req.getDisplayName().isBlank()) {
                fv.put("display_name", req.getDisplayName());
            }
            if (req.getWidgetType() != null && !req.getWidgetType().isBlank()) {
                fv.put("widget_type", req.getWidgetType());
            }
            if (req.getRiskLevel() != null && !req.getRiskLevel().isBlank()) {
                fv.put("risk_level", req.getRiskLevel());
            }
            if (req.getRiskTags() != null && !req.getRiskTags().isEmpty()) {
                fv.put("risk_tags", req.getRiskTags());
            }
            if (req.getRiskReasons() != null && !req.getRiskReasons().isEmpty()) {
                fv.put("risk_reasons", req.getRiskReasons());
            }
            if (req.getLocators() != null && !req.getLocators().isEmpty()) {
                fv.put("locators", req.getLocators());
            }
            if (req.getLocatorChain() != null && !req.getLocatorChain().isEmpty()) {
                fv.put("locator_chain", req.getLocatorChain());
            }
            if (req.getValidateResult() != null && !req.getValidateResult().isEmpty()) {
                fv.put("validate_result", req.getValidateResult());
                fv.put("last_validation", req.getValidateResult());
            }
            if (req.getValidatedAt() != null && !req.getValidatedAt().isBlank()) {
                fv.put("validated_at", req.getValidatedAt());
            }
            if (req.getEnvTag() != null && !req.getEnvTag().isBlank()) {
                fv.put("env_tag", req.getEnvTag());
            }
            if (req.getControlTag() != null && !req.getControlTag().isBlank()) {
                fv.put("control_tag", req.getControlTag());
            }
            if (req.getWaitRule() != null && !req.getWaitRule().isEmpty()) {
                fv.put("wait_rule", req.getWaitRule());
            }
            if (req.getTapX() != null) fv.put("tap_x", req.getTapX());
            if (req.getTapY() != null) fv.put("tap_y", req.getTapY());
            return fv.isEmpty() ? null : objectMapper.writeValueAsString(fv);
        } catch (Exception e) {
            return null;
        }
    }

    private String snapshotPoolExtended(ControlPool p) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "element_name", p.getElementName() != null ? p.getElementName() : "",
                    "app_package", p.getAppPackage() != null ? p.getAppPackage() : "",
                    "page_name", p.getPageName() != null ? p.getPageName() : "",
                    "locator_type", p.getLocatorType() != null ? p.getLocatorType() : "",
                    "locator_value", p.getLocatorValue() != null ? p.getLocatorValue() : "",
                    "status", p.getStatus() != null ? p.getStatus() : "active",
                    "platform", p.getPlatform() != null ? p.getPlatform().name() : "android"
            ));
        } catch (Exception e) {
            return snapshotPool(p);
        }
    }

    private String snapshotBinding(PrivateControlBinding b) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "task_id", b.getTaskId(),
                    "step_index", b.getStepIndex(),
                    "element_name", b.getElementName(),
                    "locator_type", b.getLocatorType(),
                    "locator_value", b.getLocatorValue()
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String generatePoolKey(String appPackage, String pageName, String elementName,
                                   Long teamId, String platform, String versionTag, String envTag) {
        try {
            String raw = String.join(":",
                    teamId != null ? teamId.toString() : "*",
                    appPackage != null ? appPackage : "",
                    platform != null && !platform.isBlank() ? platform : "both",
                    normalizeDim(versionTag),
                    normalizeDim(envTag),
                    pageName != null ? pageName : "",
                    elementName != null ? elementName : "");
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String generatePoolKey(String appPackage, String pageName, String elementName) {
        return generatePoolKey(appPackage, pageName, elementName, null, "both", "*", "*");
    }

    private ResolvedControl fromPool(ControlPool pool, String key, String elementName, String source, Integer stepIndex) {
        Map<String, Object> locators = buildLocatorsFromPool(pool);
        List<Map<String, Object>> chain = buildChainFromPool(pool);
        Map<String, Object> fv = parseFeatureVector(pool);
        Map<String, Object> waitRule = null;
        Object wr = fv.get("wait_rule");
        if (wr instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, Object> waitRuleMap = new LinkedHashMap<>();
            map.forEach((k, v) -> waitRuleMap.put(String.valueOf(k), v));
            waitRule = waitRuleMap;
        }
        return new ResolvedControl(key, elementName, pool.getLocatorType(), pool.getLocatorValue(),
                source, stepIndex, locators, chain, waitRule);
    }

    Map<String, Object> parseFeatureVector(ControlPool pool) {
        if (pool == null || pool.getFeatureVector() == null || pool.getFeatureVector().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(pool.getFeatureVector(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    Map<String, Object> buildLocatorsFromPool(ControlPool pool) {
        Map<String, Object> fv = parseFeatureVector(pool);
        Object locs = fv.get("locators");
        if (locs instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        if (pool.getLocatorType() != null && pool.getLocatorValue() != null) {
            fallback.put(normalizePoolLocatorKey(pool.getLocatorType()), pool.getLocatorValue());
        }
        return fallback;
    }

    List<Map<String, Object>> buildChainFromPool(ControlPool pool) {
        Map<String, Object> fv = parseFeatureVector(pool);
        Object chain = fv.get("locator_chain");
        if (!(chain instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> row = new LinkedHashMap<>();
                m.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }

    private String normalizePoolLocatorKey(String locatorType) {
        if (locatorType == null) return "id";
        return switch (locatorType) {
            case "xpath", "accessibility", "ai", "image" -> locatorType;
            default -> "id".equals(locatorType) ? "id" : locatorType;
        };
    }

    @SuppressWarnings("unchecked")
    private String mergeFeatureVector(String existing, Map<String, Object> body) {
        try {
            Map<String, Object> fv = existing != null && !existing.isBlank()
                    ? objectMapper.readValue(existing, new TypeReference<Map<String, Object>>() {})
                    : new LinkedHashMap<>();
            if (body.get("locators") instanceof Map<?, ?> locs) {
                Map<String, Object> merged = new LinkedHashMap<>();
                locs.forEach((k, v) -> merged.put(String.valueOf(k), v));
                fv.put("locators", merged);
            }
            if (body.get("locator_chain") instanceof List<?> chain) {
                fv.put("locator_chain", chain);
            }
            if (body.get("validate_result") instanceof Map<?, ?> vr) {
                Map<String, Object> mergedVr = new LinkedHashMap<>();
                vr.forEach((k, v) -> mergedVr.put(String.valueOf(k), v));
                fv.put("validate_result", mergedVr);
                fv.put("last_validation", mergedVr);
            }
            if (body.get("validated_at") != null) {
                fv.put("validated_at", body.get("validated_at").toString());
            }
            if (body.get("risk_level") != null) {
                fv.put("risk_level", body.get("risk_level").toString());
            }
            if (body.get("risk_tags") instanceof List<?> tags) {
                fv.put("risk_tags", tags);
            }
            if (body.get("wait_rule") instanceof Map<?, ?> wr) {
                Map<String, Object> mergedWr = new LinkedHashMap<>();
                wr.forEach((k, v) -> mergedWr.put(String.valueOf(k), v));
                fv.put("wait_rule", mergedWr);
            }
            return fv.isEmpty() ? null : objectMapper.writeValueAsString(fv);
        } catch (Exception e) {
            return existing;
        }
    }
}
