package com.atp.platform.service;

import com.atp.platform.entity.ControlPool;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.ControlChangeLogRepository;
import com.atp.platform.repository.ControlPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** M3 平台治理：批量校验、不稳定统计、校验归档 */
@Service
@RequiredArgsConstructor
public class ControlGovernanceService {

    public static final int FAIL_STREAK_ARCHIVE_THRESHOLD = 3;

    private final ControlPoolRepository poolRepository;
    private final ControlPoolService controlPoolService;
    private final ControlChangeLogRepository changeLogRepository;
    private final LocatorFailureReportService failureReportService;
    private final PythonExecutorClient executorClient;

    @Transactional
    public Map<String, Object> batchValidate(Map<String, Object> body, Long operatorId) {
        String serial = str(body.get("serial_number"));
        String platform = str(body.get("platform"));
        if (serial.isBlank()) {
            throw new AppException("INVALID", "serial_number 必填", HttpStatus.BAD_REQUEST);
        }
        if (platform.isBlank()) platform = "android";

        List<ControlPool> pools = resolvePoolsForBatch(body);
        List<Map<String, Object>> results = new ArrayList<>();
        int failed = 0;
        int archived = 0;
        int passed = 0;

        for (ControlPool pool : pools) {
            if ("archived".equals(pool.getStatus())) {
                continue;
            }
            Map<String, Object> locators = controlPoolService.buildLocatorsFromPool(pool);
            List<Map<String, Object>> chain = controlPoolService.buildChainFromPool(pool);
            Map<String, Object> vr = executorClient.validateLocatorOnScreen(serial, platform, locators, chain);
            boolean valid = Boolean.TRUE.equals(vr.get("valid"));
            Map<String, Object> record = controlPoolService.recordValidationOutcome(pool, vr, operatorId);
            if (!valid) {
                Map<String, Object> attemptMeta = new LinkedHashMap<>();
                attemptMeta.put("fail_streak", record.getOrDefault("fail_streak", 0));
                attemptMeta.put("error", vr.getOrDefault("error", ""));
                attemptMeta.put("matched_by", vr.getOrDefault("matched_by", ""));
                failureReportService.recordFailure(
                        pool.getId(), pool.getElementName(), pool.getAppPackage(),
                        failureReportService.classifyFailure(attemptMeta),
                        attemptMeta, null, null);
            }
            if (Boolean.TRUE.equals(record.get("archived"))) {
                archived++;
            }
            if (valid) {
                passed++;
            } else {
                failed++;
            }
            results.add(Map.of(
                    "pool_id", pool.getId(),
                    "element_name", pool.getElementName(),
                    "app_package", pool.getAppPackage() != null ? pool.getAppPackage() : "",
                    "page_name", pool.getPageName() != null ? pool.getPageName() : "",
                    "valid", valid,
                    "matched_by", vr.getOrDefault("matched_by", ""),
                    "error", vr.getOrDefault("error", ""),
                    "fail_streak", record.getOrDefault("fail_streak", 0),
                    "suggest_archive", record.getOrDefault("suggest_archive", false),
                    "archived", record.getOrDefault("archived", false),
                    "risk_level", controlPoolService.parseFeatureVector(pool).getOrDefault("risk_level", "")
            ));
        }

        List<Map<String, Object>> unstableTop = unstableStats(30, 10);
        return Map.of(
                "total", pools.size(),
                "passed", passed,
                "failed", failed,
                "archived", archived,
                "results", results,
                "unstable_top", unstableTop
        );
    }

    public List<Map<String, Object>> unstableStats(int days, int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days));
        List<Object[]> rows = changeLogRepository.countChangesByPoolSince(since, PageRequest.of(0, Math.max(1, limit)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : rows) {
            Long poolId = (Long) row[0];
            long cnt = (Long) row[1];
            poolRepository.findById(poolId).ifPresent(pool -> out.add(Map.of(
                    "pool_id", poolId,
                    "element_name", pool.getElementName(),
                    "app_package", pool.getAppPackage() != null ? pool.getAppPackage() : "",
                    "change_count", cnt,
                    "version_tag", pool.getVersionTag() != null ? pool.getVersionTag() : "",
                    "env_tag", pool.getEnvTag() != null ? pool.getEnvTag() : ""
            )));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<ControlPool> resolvePoolsForBatch(Map<String, Object> body) {
        Object idsObj = body.get("pool_ids");
        if (idsObj instanceof List<?> ids && !ids.isEmpty()) {
            List<Long> poolIds = new ArrayList<>();
            for (Object id : ids) {
                if (id instanceof Number n) poolIds.add(n.longValue());
            }
            if (!poolIds.isEmpty()) {
                return poolRepository.findAllById(poolIds);
            }
        }
        String appPackage = str(body.get("app_package"));
        String pageName = str(body.get("page_name"));
        String platform = str(body.get("platform"));
        String versionTag = str(body.get("version_tag"));
        String envTag = str(body.get("env_tag"));
        Long teamId = num(body.get("team_id"));
        String status = str(body.get("status"));
        if (status.isBlank()) status = "active";
        return poolRepository.search(appPackage, pageName, platform, versionTag, envTag, teamId, status,
                PageRequest.of(0, 500)).getContent();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static Long num(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o == null) return null;
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
