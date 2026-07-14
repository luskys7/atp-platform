package com.atp.platform.service;

import com.atp.platform.entity.TestReport;
import com.atp.platform.entity.TestTask;
import com.atp.platform.repository.TestCaseRepository;
import com.atp.platform.repository.TestReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DefectLinkService {

    private final TestCaseRepository caseRepository;
    private final TestReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern PERF_PATTERN = Pattern.compile("ATP_PERF_METRICS:(.+)");

    public void onTaskFinished(TestTask task, TestReport report, List<String> logMessages) {
        if (task.getSourceCaseId() != null) {
            caseRepository.findById(task.getSourceCaseId()).ifPresent(c -> {
                if (c.getDefectId() != null && !c.getDefectId().isBlank()) {
                    report.setDefectId(c.getDefectId());
                }
            });
        }
        String perf = extractPerfMetrics(logMessages);
        if (perf != null) {
            report.setPerfMetricsJson(perf);
        }
        reportRepository.save(report);
    }

    public Map<String, Object> getDefectInfo(Long taskId) {
        Map<String, Object> info = new LinkedHashMap<>();
        reportRepository.findByTaskId(taskId).ifPresent(r -> {
            info.put("defect_id", r.getDefectId());
            info.put("perf_metrics", parseJson(r.getPerfMetricsJson()));
        });
        return info;
    }

    private String extractPerfMetrics(List<String> logs) {
        if (logs == null) return null;
        for (String msg : logs) {
            if (msg == null) continue;
            Matcher m = PERF_PATTERN.matcher(msg);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, JsonNode.class);
        } catch (Exception e) {
            return json;
        }
    }
}
