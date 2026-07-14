package com.atp.platform.service;

import com.atp.platform.entity.DataSetRow;
import com.atp.platform.entity.PlatformMessage;
import com.atp.platform.entity.TestTask;
import com.atp.platform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 删除任务前清理关联数据，避免外键约束冲突 */
@Service
public class TaskDeletionService {

    private final ExecutionLogRepository logRepository;
    private final TaskExecutionRepository executionRepository;
    private final TestReportRepository reportRepository;
    private final FailureSnapshotRepository failureSnapshotRepository;
    private final ManualInterventionRepository manualInterventionRepository;
    private final RecordingResourceRepository recordingRepository;
    private final ExecutionStepMarkerRepository executionStepMarkerRepository;
    private final PrivateControlBindingRepository privateControlBindingRepository;
    private final CiJobRecordRepository ciJobRecordRepository;
    private final CaseHealingRecordRepository caseHealingRecordRepository;
    private final DataFactoryRunRepository dataFactoryRunRepository;
    private final SuiteRunItemRepository suiteRunItemRepository;
    private final PlatformMessageRepository platformMessageRepository;
    private final DataSetRowRepository dataSetRowRepository;
    private final TestTaskRepository taskRepository;
    private final DeviceService deviceService;
    private final TestAccountService testAccountService;

    public TaskDeletionService(ExecutionLogRepository logRepository,
                               TaskExecutionRepository executionRepository,
                               TestReportRepository reportRepository,
                               FailureSnapshotRepository failureSnapshotRepository,
                               ManualInterventionRepository manualInterventionRepository,
                               RecordingResourceRepository recordingRepository,
                               ExecutionStepMarkerRepository executionStepMarkerRepository,
                               PrivateControlBindingRepository privateControlBindingRepository,
                               CiJobRecordRepository ciJobRecordRepository,
                               CaseHealingRecordRepository caseHealingRecordRepository,
                               DataFactoryRunRepository dataFactoryRunRepository,
                               SuiteRunItemRepository suiteRunItemRepository,
                               PlatformMessageRepository platformMessageRepository,
                               DataSetRowRepository dataSetRowRepository,
                               TestTaskRepository taskRepository,
                               DeviceService deviceService,
                               TestAccountService testAccountService) {
        this.logRepository = logRepository;
        this.executionRepository = executionRepository;
        this.reportRepository = reportRepository;
        this.failureSnapshotRepository = failureSnapshotRepository;
        this.manualInterventionRepository = manualInterventionRepository;
        this.recordingRepository = recordingRepository;
        this.executionStepMarkerRepository = executionStepMarkerRepository;
        this.privateControlBindingRepository = privateControlBindingRepository;
        this.ciJobRecordRepository = ciJobRecordRepository;
        this.caseHealingRecordRepository = caseHealingRecordRepository;
        this.dataFactoryRunRepository = dataFactoryRunRepository;
        this.suiteRunItemRepository = suiteRunItemRepository;
        this.platformMessageRepository = platformMessageRepository;
        this.dataSetRowRepository = dataSetRowRepository;
        this.taskRepository = taskRepository;
        this.deviceService = deviceService;
        this.testAccountService = testAccountService;
    }

    @Transactional
    public void deleteDependents(Long taskId) {
        deviceService.releaseLocksForTask(taskId);
        testAccountService.releaseByTaskId(taskId);
        releaseDatasetRows(taskId);
        detachSuiteRunItem(taskId);
        clearRelatedMessages(taskId);
        detachChildTasks(taskId);

        reportRepository.deleteAllByTaskId(taskId);
        logRepository.deleteByTaskId(taskId);
        executionRepository.deleteByTaskId(taskId);
        reportRepository.deleteByTaskId(taskId);
        failureSnapshotRepository.deleteByTaskId(taskId);
        manualInterventionRepository.deleteByTaskId(taskId);
        recordingRepository.deleteByTaskId(taskId);
        executionStepMarkerRepository.deleteByTaskId(taskId);
        privateControlBindingRepository.deleteByTaskId(taskId);
        ciJobRecordRepository.deleteByTaskId(taskId);
        caseHealingRecordRepository.deleteByTaskId(taskId);
        dataFactoryRunRepository.deleteByTaskId(taskId);
    }

    private void releaseDatasetRows(Long taskId) {
        for (DataSetRow row : dataSetRowRepository.findByLockedByTaskId(taskId)) {
            row.setLockStatus(DataSetRow.LockStatus.idle);
            row.setLockedByTaskId(null);
            dataSetRowRepository.save(row);
        }
    }

    private void detachSuiteRunItem(Long taskId) {
        suiteRunItemRepository.findByTaskId(taskId).ifPresent(item -> {
            item.setTaskId(null);
            suiteRunItemRepository.save(item);
        });
    }

    private void clearRelatedMessages(Long taskId) {
        for (PlatformMessage msg : platformMessageRepository.findByRelatedTaskId(taskId)) {
            msg.setRelatedTaskId(null);
            platformMessageRepository.save(msg);
        }
    }

    private void detachChildTasks(Long taskId) {
        List<TestTask> children = taskRepository.findByParentTaskId(taskId);
        for (TestTask child : children) {
            child.setParentTaskId(null);
            taskRepository.save(child);
        }
    }
}
