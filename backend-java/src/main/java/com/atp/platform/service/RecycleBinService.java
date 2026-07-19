package com.atp.platform.service;

import com.atp.platform.entity.*;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecycleBinService {

    private static final int RETAIN_DAYS = 90;

    private final RecycleBinItemRepository repository;
    private final TestCaseRepository caseRepository;
    private final TestSuiteRepository suiteRepository;
    private final CommonStepRepository commonStepRepository;
    private final DataSetRepository dataSetRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ObjectMapper objectMapper;

    public void add(String resourceType, Long resourceId, String resourceName, Object snapshot, Long deletedBy) {
        try {
            RecycleBinItem item = new RecycleBinItem();
            item.setResourceType(resourceType);
            item.setResourceId(resourceId);
            item.setResourceName(resourceName);
            item.setSnapshotJson(snapshot != null ? objectMapper.writeValueAsString(snapshot) : null);
            item.setDeletedBy(deletedBy);
            item.setDeletedAt(LocalDateTime.now());
            item.setExpireAt(LocalDateTime.now().plusDays(RETAIN_DAYS));
            repository.save(item);
        } catch (Exception ignored) {
            RecycleBinItem item = new RecycleBinItem();
            item.setResourceType(resourceType);
            item.setResourceId(resourceId);
            item.setResourceName(resourceName);
            item.setDeletedBy(deletedBy);
            item.setDeletedAt(LocalDateTime.now());
            item.setExpireAt(LocalDateTime.now().plusDays(RETAIN_DAYS));
            repository.save(item);
        }
    }

    public List<Map<String, Object>> list() {
        List<RecycleBinItem> items = repository.findAllByOrderByDeletedAtDesc();
        Map<Long, User> users = loadUsers(items);
        Map<Long, String> teamNames = loadTeamNames(items);
        return items.stream().map(item -> toView(item, users, teamNames)).toList();
    }

    public Map<String, Object> stats() {
        List<RecycleBinItem> items = repository.findAll();
        LocalDateTime now = LocalDateTime.now();
        long expired = items.stream().filter(i -> i.getExpireAt() != null && !i.getExpireAt().isAfter(now)).count();
        long steps = items.stream().filter(i -> "common_step".equals(i.getResourceType())).count();
        long cases = items.stream().filter(i -> "test_case".equals(i.getResourceType()) || "test_suite".equals(i.getResourceType())).count();
        long datasets = items.stream().filter(i -> "data_set".equals(i.getResourceType())).count();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total", items.size());
        row.put("common_step", steps);
        row.put("case_suite", cases);
        row.put("data_set", datasets);
        row.put("expired", expired);
        row.put("expired_common_step", items.stream().filter(i -> "common_step".equals(i.getResourceType()) && isExpired(i, now)).count());
        row.put("expired_case_suite", items.stream().filter(i -> ("test_case".equals(i.getResourceType()) || "test_suite".equals(i.getResourceType())) && isExpired(i, now)).count());
        row.put("expired_data_set", items.stream().filter(i -> "data_set".equals(i.getResourceType()) && isExpired(i, now)).count());
        return row;
    }

    @Transactional
    public RecycleBinItem restore(Long id) {
        RecycleBinItem item = repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "回收站记录不存在", HttpStatus.NOT_FOUND));
        if (item.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new AppException("EXPIRED", "已超过 90 天保留期，无法还原", HttpStatus.BAD_REQUEST);
        }
        switch (item.getResourceType()) {
            case "test_case" -> restoreTestCase(item);
            case "test_suite" -> restoreTestSuite(item);
            case "common_step" -> restoreCommonStep(item);
            case "data_set" -> restoreDataSet(item);
            default -> throw new AppException("UNSUPPORTED", "不支持还原该资源类型: " + item.getResourceType(), HttpStatus.BAD_REQUEST);
        }
        repository.delete(item);
        return item;
    }

    @Transactional
    public Map<String, Object> batchRestore(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new AppException("INVALID", "请选择要还原的资源", HttpStatus.BAD_REQUEST);
        }
        List<RecycleBinItem> items = repository.findAllById(ids);
        if (items.isEmpty()) {
            throw new AppException("NOT_FOUND", "未找到选中的回收站记录", HttpStatus.NOT_FOUND);
        }
        Set<String> types = items.stream().map(RecycleBinItem::getResourceType).collect(Collectors.toSet());
        if (types.size() > 1) {
            throw new AppException("INVALID", "批量还原仅可一次性处理同类型资源，请分开操作", HttpStatus.BAD_REQUEST);
        }
        Set<String> projects = items.stream().map(this::extractProjectKey).collect(Collectors.toSet());
        if (projects.size() > 1) {
            throw new AppException("INVALID", "批量还原仅可一次性处理同项目资源，跨项目请分开操作", HttpStatus.BAD_REQUEST);
        }
        Set<String> teams = items.stream().map(this::extractTeamKey).collect(Collectors.toSet());
        if (teams.size() > 1) {
            throw new AppException("INVALID", "批量还原仅可一次性处理同团队资源，跨团队请分开操作", HttpStatus.BAD_REQUEST);
        }

        int restored = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                restore(id);
                restored++;
            } catch (Exception e) {
                errors.add(id + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restored", restored);
        result.put("errors", errors);
        return result;
    }

    public void purge(Long id) {
        if (!repository.existsById(id)) {
            throw new AppException("NOT_FOUND", "回收站记录不存在", HttpStatus.NOT_FOUND);
        }
        repository.deleteById(id);
    }

    @Transactional
    public Map<String, Object> batchPurge(List<Long> ids) {
        int purged = 0;
        for (Long id : ids) {
            if (repository.existsById(id)) {
                repository.deleteById(id);
                purged++;
            }
        }
        return Map.of("purged", purged);
    }

    @Transactional
    public Map<String, Object> clearAll() {
        long count = repository.count();
        repository.deleteAll();
        return Map.of("purged", count);
    }

    public Map<String, Object> preview(Long id) {
        RecycleBinItem item = repository.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "回收站记录不存在", HttpStatus.NOT_FOUND));
        Map<Long, User> users = loadUsers(List.of(item));
        Map<Long, String> teamNames = loadTeamNames(List.of(item));
        Map<String, Object> row = toView(item, users, teamNames);
        Object snapshot = null;
        if (item.getSnapshotJson() != null && !item.getSnapshotJson().isBlank()) {
            try {
                snapshot = objectMapper.readValue(item.getSnapshotJson(), Object.class);
            } catch (Exception e) {
                snapshot = item.getSnapshotJson();
            }
        }
        row.put("snapshot", snapshot);
        return row;
    }

    private Map<String, Object> toView(RecycleBinItem item, Map<Long, User> users, Map<Long, String> teamNames) {
        LocalDateTime now = LocalDateTime.now();
        boolean expired = isExpired(item, now);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("resource_type", item.getResourceType());
        row.put("resource_id", item.getResourceId());
        row.put("resource_name", item.getResourceName());
        row.put("deleted_by", item.getDeletedBy());
        User u = item.getDeletedBy() != null ? users.get(item.getDeletedBy()) : null;
        row.put("deleted_by_name", u != null
                ? (u.getDisplayName() != null && !u.getDisplayName().isBlank() ? u.getDisplayName() : u.getUsername())
                : (item.getDeletedBy() != null ? "#" + item.getDeletedBy() : "—"));
        row.put("deleted_at", item.getDeletedAt());
        row.put("expire_at", item.getExpireAt());
        row.put("expired", expired);
        row.put("status", expired ? "expired" : "active");
        Long teamId = extractTeamId(item);
        row.put("team_id", teamId);
        row.put("team_name", teamId != null ? teamNames.getOrDefault(teamId, "团队 #" + teamId) : "—");
        String projectKey = extractProjectKey(item);
        row.put("project_key", projectKey);
        row.put("project_name", projectDisplay(projectKey));
        return row;
    }

    private boolean isExpired(RecycleBinItem item, LocalDateTime now) {
        return item.getExpireAt() != null && !item.getExpireAt().isAfter(now);
    }

    private Map<Long, User> loadUsers(List<RecycleBinItem> items) {
        List<Long> ids = items.stream().map(RecycleBinItem::getDeletedBy).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, String> loadTeamNames(List<RecycleBinItem> items) {
        List<Long> ids = items.stream().map(this::extractTeamId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        try {
            return teamRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(Team::getId, Team::getName, (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Long extractTeamId(RecycleBinItem item) {
        JsonNode node = readSnapshot(item);
        if (node == null) return null;
        JsonNode team = node.get("teamId");
        if (team == null) team = node.get("team_id");
        if (team != null && !team.isNull()) {
            try { return team.asLong(); } catch (Exception ignored) { return null; }
        }
        return null;
    }

    private String extractProjectKey(RecycleBinItem item) {
        JsonNode node = readSnapshot(item);
        if (node == null) return "default";
        JsonNode key = node.get("projectKey");
        if (key == null) key = node.get("project_key");
        if (key != null && !key.isNull() && !key.asText().isBlank()) return key.asText();
        return "default";
    }

    private String extractTeamKey(RecycleBinItem item) {
        Long id = extractTeamId(item);
        return id == null ? "" : String.valueOf(id);
    }

    private String projectDisplay(String key) {
        if (key == null || key.isBlank() || "default".equals(key)) return "默认业务项目";
        if (key.startsWith("team-")) return "团队项目 " + key.substring(5);
        return key;
    }

    private JsonNode readSnapshot(RecycleBinItem item) {
        if (item.getSnapshotJson() == null || item.getSnapshotJson().isBlank()) return null;
        try {
            return objectMapper.readTree(item.getSnapshotJson());
        } catch (Exception e) {
            return null;
        }
    }

    private void restoreTestCase(RecycleBinItem item) {
        TestCase c = caseRepository.findById(item.getResourceId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "用例实体不存在", HttpStatus.NOT_FOUND));
        if (c.getDeletedAt() == null) {
            throw new AppException("ALREADY_ACTIVE", "用例未被删除或已还原", HttpStatus.BAD_REQUEST);
        }
        c.setDeletedAt(null);
        caseRepository.save(c);
    }

    private void restoreTestSuite(RecycleBinItem item) {
        TestSuite s = suiteRepository.findById(item.getResourceId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "套件实体不存在", HttpStatus.NOT_FOUND));
        if (s.getDeletedAt() == null) {
            throw new AppException("ALREADY_ACTIVE", "套件未被删除或已还原", HttpStatus.BAD_REQUEST);
        }
        s.setDeletedAt(null);
        suiteRepository.save(s);
    }

    private void restoreCommonStep(RecycleBinItem item) {
        CommonStep s = commonStepRepository.findById(item.getResourceId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "公共步骤实体不存在", HttpStatus.NOT_FOUND));
        if (s.getDeletedAt() == null) {
            throw new AppException("ALREADY_ACTIVE", "公共步骤未被删除或已还原", HttpStatus.BAD_REQUEST);
        }
        commonStepRepository.findByNameAndDeletedAtIsNull(s.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(s.getId())) {
                throw new AppException("DUPLICATE", "同名公共步骤已存在，请先处理冲突", HttpStatus.BAD_REQUEST);
            }
        });
        s.setDeletedAt(null);
        commonStepRepository.save(s);
    }

    private void restoreDataSet(RecycleBinItem item) {
        DataSet ds = dataSetRepository.findById(item.getResourceId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "数据集实体不存在", HttpStatus.NOT_FOUND));
        if (ds.getDeletedAt() == null) {
            throw new AppException("ALREADY_ACTIVE", "数据集未被删除或已还原", HttpStatus.BAD_REQUEST);
        }
        ds.setDeletedAt(null);
        dataSetRepository.save(ds);
    }
}
