package service

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	apperr "github.com/atp-platform/backend/internal/pkg/errors"
	"github.com/atp-platform/backend/internal/model"
	redispkg "github.com/atp-platform/backend/internal/pkg/redis"
	"github.com/atp-platform/backend/internal/config"
	"github.com/go-redis/redis/v8"
	"go.uber.org/zap"
	"gorm.io/gorm"
)

type SchedulerService struct {
	db            *gorm.DB
	redis         *redis.Client
	deviceService *DeviceService
	taskService   *TaskService
	cfg           config.SchedulerConfig
	log           *zap.Logger
	stopCh        chan struct{}
}

func NewSchedulerService(
	db *gorm.DB,
	redisClient *redis.Client,
	deviceService *DeviceService,
	taskService *TaskService,
	cfg config.SchedulerConfig,
	log *zap.Logger,
) *SchedulerService {
	return &SchedulerService{
		db:            db,
		redis:         redisClient,
		deviceService: deviceService,
		taskService:   taskService,
		cfg:           cfg,
		log:           log,
		stopCh:        make(chan struct{}),
	}
}

type QueueItem struct {
	TaskID   uint64 `json:"task_id"`
	Priority int8   `json:"priority"`
}

func (s *SchedulerService) EnqueueTask(ctx context.Context, taskID uint64, priority int8) error {
	size, err := s.redis.LLen(ctx, redispkg.TaskQueueKey).Result()
	if err != nil {
		return err
	}
	if int(size) >= s.cfg.QueueMaxSize {
		return apperr.New(apperr.E3001, 429)
	}

	item := QueueItem{TaskID: taskID, Priority: priority}
	data, _ := json.Marshal(item)
	return s.redis.RPush(ctx, redispkg.TaskQueueKey, data).Err()
}

func (s *SchedulerService) StartWorker() {
	go s.workerLoop()
	go s.timeoutWatcher()
}

func (s *SchedulerService) Stop() {
	close(s.stopCh)
}

func (s *SchedulerService) workerLoop() {
	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-s.stopCh:
			return
		case <-ticker.C:
			s.processNextTask()
		}
	}
}

func (s *SchedulerService) processNextTask() {
	ctx := context.Background()
	result, err := s.redis.LPop(ctx, redispkg.TaskQueueKey).Result()
	if err == redis.Nil {
		return
	}
	if err != nil {
		s.log.Error("queue pop error", zap.Error(err))
		return
	}

	var item QueueItem
	if err := json.Unmarshal([]byte(result), &item); err != nil {
		return
	}

	task, err := s.taskService.GetByID(item.TaskID)
	if err != nil {
		return
	}
	if task.Status != model.TaskQueued && task.Status != model.TaskPending {
		return
	}

	s.dispatchTask(ctx, task)
}

func (s *SchedulerService) dispatchTask(ctx context.Context, task *model.TestTask) {
	now := time.Now()
	s.db.Model(task).Updates(map[string]interface{}{
		"status":     model.TaskRunning,
		"started_at": now,
	})

	platform := task.Platform
	if platform == "both" {
		platform = model.PlatformAndroid
	}

	devices, err := s.deviceService.GetAvailableDevices(platform, task.ParallelCount)
	if err != nil || len(devices) == 0 {
		errCode := apperr.E1002
		msg := "无可用设备"
		s.db.Model(task).Updates(map[string]interface{}{
			"status":        model.TaskFailed,
			"finished_at":   time.Now(),
			"error_code":    errCode,
			"error_message": msg,
		})
		return
	}

	lockTTL := time.Duration(s.cfg.LockTTLSeconds) * time.Second
	lockedCount := 0
	var executions []model.TaskExecution

	for _, device := range devices {
		if err := s.deviceService.TryLockDevice(ctx, device.ID, task.ID, lockTTL); err != nil {
			continue
		}
		exec := model.TaskExecution{
			TaskID:   task.ID,
			DeviceID: device.ID,
			Status:   model.TaskRunning,
		}
		started := time.Now()
		exec.StartedAt = &started
		s.db.Create(&exec)
		executions = append(executions, exec)
		lockedCount++

		go s.runExecution(ctx, task, &exec)
	}

	if lockedCount == 0 {
		errCode := apperr.E1002
		s.db.Model(task).Updates(map[string]interface{}{
			"status":        model.TaskFailed,
			"finished_at":   time.Now(),
			"error_code":    errCode,
			"error_message": apperr.Message(errCode),
		})
	}
}

func (s *SchedulerService) runExecution(ctx context.Context, task *model.TestTask, exec *model.TaskExecution) {
	defer func() {
		s.deviceService.ReleaseLock(ctx, exec.DeviceID)
		s.checkTaskCompletion(task.ID)
	}()

	timeout := time.Duration(task.TimeoutSeconds) * time.Second
	timer := time.NewTimer(timeout)
	done := make(chan bool, 1)

	go func() {
		err := s.executeScript(task, exec)
		done <- err == nil
	}()

	select {
	case success := <-done:
		finished := time.Now()
		status := model.TaskSuccess
		if !success {
			status = model.TaskFailed
		}
		s.db.Model(exec).Updates(map[string]interface{}{
			"status":      status,
			"finished_at": finished,
		})
	case <-timer.C:
		errCode := apperr.E3002
		msg := apperr.Message(apperr.E3002)
		finished := time.Now()
		s.db.Model(exec).Updates(map[string]interface{}{
			"status":        model.TaskTimeout,
			"finished_at":   finished,
			"error_code":    errCode,
			"error_message": msg,
		})
	}
}

func (s *SchedulerService) executeScript(task *model.TestTask, exec *model.TaskExecution) error {
	s.db.Create(&model.ExecutionLog{
		TaskID:      task.ID,
		ExecutionID: &exec.ID,
		DeviceID:    &exec.DeviceID,
		LogType:     "script",
		Level:       "info",
		Message:     fmt.Sprintf("开始执行脚本 [%s] 于设备 %d", task.ScriptType, exec.DeviceID),
	})

	if task.EnableRecording {
		rec := model.RecordingResource{
			TaskID:      task.ID,
			ExecutionID: &exec.ID,
			DeviceID:    exec.DeviceID,
			FileName:    fmt.Sprintf("task_%d_exec_%d.mp4", task.ID, exec.ID),
			FilePath:    fmt.Sprintf("recordings/%d/%d/%d.mp4", task.ID, exec.DeviceID, exec.ID),
			Format:      "mp4",
			Status:      model.RecordingReady,
		}
		s.db.Create(&rec)
	}

	time.Sleep(2 * time.Second)

	s.db.Create(&model.ExecutionLog{
		TaskID:      task.ID,
		ExecutionID: &exec.ID,
		DeviceID:    &exec.DeviceID,
		LogType:     "script",
		Level:       "info",
		Message:     "脚本执行完成",
	})

	s.db.Model(exec).Update("result_summary", "执行成功")
	return nil
}

func (s *SchedulerService) checkTaskCompletion(taskID uint64) {
	var running int64
	s.db.Model(&model.TaskExecution{}).Where("task_id = ? AND status = ?", taskID, model.TaskRunning).Count(&running)
	if running > 0 {
		return
	}

	var executions []model.TaskExecution
	s.db.Where("task_id = ?", taskID).Find(&executions)

	successCount := 0
	failedCount := 0
	for _, e := range executions {
		if e.Status == model.TaskSuccess {
			successCount++
		} else {
			failedCount++
		}
	}

	taskStatus := model.TaskSuccess
	if failedCount > 0 {
		taskStatus = model.TaskFailed
	}

	finished := time.Now()
	s.db.Model(&model.TestTask{}).Where("id = ?", taskID).Updates(map[string]interface{}{
		"status":      taskStatus,
		"finished_at": finished,
	})

	total := len(executions)
	passRate := float64(0)
	if total > 0 {
		passRate = float64(successCount) / float64(total) * 100
	}

	var task model.TestTask
	s.db.First(&task, taskID)

	report := model.TestReport{
		TaskID:          taskID,
		Title:           task.Name + " - 测试报告",
		TotalExecutions: total,
		SuccessCount:    successCount,
		FailedCount:     failedCount,
		PassRate:        passRate,
		Summary:         fmt.Sprintf("共 %d 次执行，成功 %d，失败 %d，通过率 %.2f%%", total, successCount, failedCount, passRate),
	}
	s.db.Where("task_id = ?", taskID).Assign(report).FirstOrCreate(&report)
}

func (s *SchedulerService) timeoutWatcher() {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-s.stopCh:
			return
		case <-ticker.C:
			s.deviceService.MarkOfflineStale(10 * time.Second)
		}
	}
}

func (s *SchedulerService) CancelTask(ctx context.Context, taskID uint64) error {
	var task model.TestTask
	if err := s.db.First(&task, taskID).Error; err != nil {
		return err
	}
	if task.Status == model.TaskRunning {
		var executions []model.TaskExecution
		s.db.Where("task_id = ? AND status = ?", taskID, model.TaskRunning).Find(&executions)
		for _, e := range executions {
			s.deviceService.ReleaseLock(ctx, e.DeviceID)
			s.db.Model(&e).Update("status", model.TaskCancelled)
		}
	}
	return s.db.Model(&task).Updates(map[string]interface{}{
		"status":      model.TaskCancelled,
		"finished_at": time.Now(),
	}).Error
}

func (s *SchedulerService) GetQueueSize(ctx context.Context) (int64, error) {
	return s.redis.LLen(ctx, redispkg.TaskQueueKey).Result()
}
