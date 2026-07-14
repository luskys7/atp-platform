package com.atp.platform.service;

import com.atp.platform.entity.LocatorFailureRecord;
import com.atp.platform.repository.LocatorFailureRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** M4-08 定位失败分层报表 */
@Service
@RequiredArgsConstructor
public class LocatorFailureReportService {

    private final LocatorFailureRecordRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public LocatorFailureRecord recordFailure(Long poolId, String elementName, String appPackage,
                                              String failureClass, Map<String, Object> attempts,
                                              Long taskId, Long executionId) {
        LocatorFailureRecord rec = new LocatorFailureRecord();
        rec.setPoolId(poolId);
        rec.setElementName(elementName != null ? elementName : "");
        rec.setAppPackage(appPackage);
        rec.setFailureClass(failureClass != null ? failureClass : classifyFailure(attempts));
        rec.setTaskId(taskId);
        rec.setExecutionId(executionId);
        try {
            if (attempts != null) {
                rec.setAttemptsJson(objectMapper.writeValueAsString(attempts));
            }
        } catch (Exception ignored) {
        }
        return repository.save(rec);
    }

    public String classifyFailure(Map<String, Object> attempts) {
        if (attempts == null) return "timing";
        Object streak = attempts.get("fail_streak");
        if (streak instanceof Number n && n.intValue() >= 3) {
            return "permanent";
        }
        Object err = attempts.get("error");
        if (err != null && String.valueOf(err).contains("device")) {
            return "device";
        }
        return "timing";
    }

    public Map<String, Object> stats(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, days));
        Map<String, Long> byClass = new LinkedHashMap<>();
        for (Object[] row : repository.countByFailureClassSince(since)) {
            byClass.put(String.valueOf(row[0]), (Long) row[1]);
        }
        List<Map<String, Object>> topElements = new ArrayList<>();
        for (Object[] row : repository.topFailedElementsSince(since, PageRequest.of(0, 20))) {
            topElements.add(Map.of(
                    "element_name", row[0],
                    "count", row[1]
            ));
        }
        long total = byClass.values().stream().mapToLong(Long::longValue).sum();
        return Map.of(
                "days", days,
                "total", total,
                "by_class", byClass,
                "timing", byClass.getOrDefault("timing", 0L),
                "device", byClass.getOrDefault("device", 0L),
                "permanent", byClass.getOrDefault("permanent", 0L),
                "top_elements", topElements
        );
    }
}
