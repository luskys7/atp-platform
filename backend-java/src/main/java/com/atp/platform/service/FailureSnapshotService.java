package com.atp.platform.service;

import com.atp.platform.entity.FailureSnapshot;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.FailureSnapshotRepository;
import com.atp.platform.repository.TestTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailureSnapshotService {

    private final FailureSnapshotRepository repository;
    private final TestTaskRepository taskRepository;
    private final TeamScopeService teamScope;
    private final ObjectMapper objectMapper;

    private static final Pattern CRASH_PATTERN = Pattern.compile("ATP_CRASH_LOG:(.+)");
    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("ATP_FAILURE_SNAPSHOT:(.+)");

    public List<FailureSnapshot> listByTask(Long taskId) {
        TestTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));
        teamScope.assertTeamAccess(task.getTeamId());
        return repository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    public void ingestFromLogs(Long taskId, Long executionId, Long deviceId, List<String> logMessages) {
        if (logMessages == null || logMessages.isEmpty()) return;
        String crashJson = null;
        String snapshotJson = null;
        String screenshotPath = null;
        for (String msg : logMessages) {
            if (msg == null) continue;
            var cm = CRASH_PATTERN.matcher(msg);
            if (cm.find()) crashJson = cm.group(1);
            var sm = SNAPSHOT_PATTERN.matcher(msg);
            if (sm.find()) {
                snapshotJson = sm.group(1);
                try {
                    JsonNode node = objectMapper.readTree(snapshotJson);
                    if (node.has("screenshot_path")) {
                        screenshotPath = node.get("screenshot_path").asText("");
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (crashJson == null && snapshotJson == null) return;

        FailureSnapshot snap = new FailureSnapshot();
        snap.setTaskId(taskId);
        snap.setExecutionId(executionId);
        snap.setDeviceId(deviceId);
        snap.setCrashLog(crashJson);
        snap.setSnapshotJson(snapshotJson);
        snap.setScreenshotPath(screenshotPath);
        repository.save(snap);
        log.info("Saved failure snapshot task={} exec={}", taskId, executionId);
    }
}
