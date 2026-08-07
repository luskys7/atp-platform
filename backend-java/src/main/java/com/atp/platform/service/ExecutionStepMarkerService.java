package com.atp.platform.service;

import com.atp.platform.entity.ExecutionStepMarker;
import com.atp.platform.entity.RecordingResource;
import com.atp.platform.entity.TaskExecution;
import com.atp.platform.entity.TestTask;
import com.atp.platform.exception.AppException;
import com.atp.platform.repository.ExecutionStepMarkerRepository;
import com.atp.platform.repository.RecordingResourceRepository;
import com.atp.platform.repository.TaskExecutionRepository;
import com.atp.platform.repository.TestTaskRepository;
import com.atp.platform.service.PythonExecutorClient.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionStepMarkerService {

    private final ExecutionStepMarkerRepository markerRepository;
    private final RecordingResourceRepository recordingRepository;
    private final TaskExecutionRepository executionRepository;
    private final TestTaskRepository taskRepository;
    private final RecordingService recordingService;

    private static final Pattern STEP_BEGIN = Pattern.compile(
            "ATP_STEP_BEGIN step=(\\d+) type=(\\w+) offset_ms=(\\d+)(?: display=([^\\s]+))?");
    private static final Pattern STEP_END = Pattern.compile(
            "ATP_STEP_END step=(\\d+) status=(ok|fail|skip|interrupt|exception|ignore) offset_ms=(\\d+)(?: error=(.+))?");
    private static final Pattern LEGACY_STEP_START = Pattern.compile("ATP_STEP_START:step=(\\d+):type=(\\w+)");
    private static final Pattern CHECKPOINT_FAILED = Pattern.compile("CHECKPOINT_FAILED:step=(\\d+)");

    @Transactional
    public void ingestFromLogs(Long taskId, Long executionId, Long deviceId, Long recordingId,
                               List<LogEntry> logs) {
        if (logs == null || logs.isEmpty()) return;
        Map<Integer, ExecutionStepMarker> pending = new LinkedHashMap<>();
        Integer failedStep = null;
        String failureMessage = null;

        for (LogEntry entry : logs) {
            if (entry == null || entry.message() == null) continue;
            String msg = entry.message().trim();

            Matcher begin = STEP_BEGIN.matcher(msg);
            if (begin.find()) {
                int step = Integer.parseInt(begin.group(1));
                ExecutionStepMarker marker = new ExecutionStepMarker();
                marker.setTaskId(taskId);
                marker.setExecutionId(executionId);
                marker.setDeviceId(deviceId);
                marker.setRecordingId(recordingId);
                marker.setStepIndex(step);
                marker.setStepType(begin.group(2));
                marker.setVideoOffsetMs(Long.parseLong(begin.group(3)));
                if (begin.group(4) != null) {
                    marker.setDisplayName(begin.group(4).replace('_', ' '));
                }
                marker.setLogLine(truncate(msg, 512));
                pending.put(step, marker);
                continue;
            }

            Matcher legacy = LEGACY_STEP_START.matcher(msg);
            if (legacy.find()) {
                int step = Integer.parseInt(legacy.group(1));
                ExecutionStepMarker marker = new ExecutionStepMarker();
                marker.setTaskId(taskId);
                marker.setExecutionId(executionId);
                marker.setDeviceId(deviceId);
                marker.setRecordingId(recordingId);
                marker.setStepIndex(step);
                marker.setStepType(legacy.group(2));
                marker.setVideoOffsetMs(0L);
                marker.setLogLine(truncate(msg, 512));
                pending.put(step, marker);
                continue;
            }

            Matcher end = STEP_END.matcher(msg);
            if (end.find()) {
                int step = Integer.parseInt(end.group(1));
                ExecutionStepMarker marker = pending.get(step);
                if (marker == null) {
                    marker = new ExecutionStepMarker();
                    marker.setTaskId(taskId);
                    marker.setExecutionId(executionId);
                    marker.setDeviceId(deviceId);
                    marker.setRecordingId(recordingId);
                    marker.setStepIndex(step);
                    pending.put(step, marker);
                }
                marker.setStatus(end.group(2));
                marker.setEndOffsetMs(Long.parseLong(end.group(3)));
                if (end.group(4) != null) {
                    marker.setErrorMessage(truncate(end.group(4).replace('_', ' '), 512));
                }
                if ("fail".equals(end.group(2)) || "interrupt".equals(end.group(2))) {
                    failedStep = step;
                    failureMessage = marker.getErrorMessage();
                }
                continue;
            }

            Matcher cp = CHECKPOINT_FAILED.matcher(msg);
            if (cp.find()) {
                failedStep = Integer.parseInt(cp.group(1));
            }
        }

        if (pending.isEmpty()) return;
        markerRepository.saveAll(pending.values());
        log.info("Saved {} step markers task={} exec={}", pending.size(), taskId, executionId);
    }

    public List<ExecutionStepMarker> listByTask(Long taskId) {
        return markerRepository.findByTaskIdOrderByStepIndexAscVideoOffsetMsAsc(taskId);
    }

    public Map<String, Object> failurePlayback(Long taskId) {
        TestTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "任务不存在", HttpStatus.NOT_FOUND));

        TaskExecution failedExec = executionRepository.findByTaskId(taskId).stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .filter(e -> e.getStatus() == TestTask.TaskStatus.failed
                        || e.getStatus() == TestTask.TaskStatus.timeout)
                .findFirst()
                .orElse(null);

        Integer failedStep = resolveFailedStep(task, failedExec);
        ExecutionStepMarker marker = failedStep != null
                ? markerRepository.findFirstByTaskIdAndStepIndexOrderByCreatedAtDesc(taskId, failedStep).orElse(null)
                : null;

        RecordingResource recording = recordingRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .filter(r -> r.getStatus() == RecordingResource.RecordingStatus.ready)
                .findFirst()
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", taskId);
        result.put("task_status", task.getStatus() != null ? task.getStatus().name() : "");
        result.put("failed_step_index", failedStep);
        result.put("failure_message", task.getErrorMessage() != null ? task.getErrorMessage()
                : (marker != null ? marker.getErrorMessage() : null));

        if (marker != null) {
            result.put("step_display_name", marker.getDisplayName());
            result.put("video_offset_ms", marker.getVideoOffsetMs());
            result.put("step_type", marker.getStepType());
        } else {
            result.put("video_offset_ms", 0);
        }

        if (recording != null) {
            result.put("recording_id", recording.getId());
            result.put("playback_url", recordingService.getPlaybackUrl(recording.getId()));
            if (marker != null && marker.getRecordingId() == null) {
                marker.setRecordingId(recording.getId());
                markerRepository.save(marker);
            }
        } else {
            result.put("recording_id", null);
            result.put("playback_url", null);
        }

        return result;
    }

    private Integer resolveFailedStep(TestTask task, TaskExecution failedExec) {
        if (failedExec != null && failedExec.getErrorMessage() != null) {
            Matcher cp = CHECKPOINT_FAILED.matcher(failedExec.getErrorMessage());
            if (cp.find()) {
                return Integer.parseInt(cp.group(1));
            }
        }
        return markerRepository.findByTaskIdOrderByStepIndexAscVideoOffsetMsAsc(task.getId()).stream()
                .filter(m -> "fail".equals(m.getStatus()) || "interrupt".equals(m.getStatus()))
                .map(ExecutionStepMarker::getStepIndex)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
