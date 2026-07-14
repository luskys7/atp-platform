package service

import (
	"context"
	"encoding/json"

	"github.com/atp-platform/backend/internal/model"
	"gorm.io/gorm"
)

type TaskService struct {
	db *gorm.DB
}

func NewTaskService(db *gorm.DB) *TaskService {
	return &TaskService{db: db}
}

type CreateTaskRequest struct {
	Name            string               `json:"name" binding:"required"`
	Description     string               `json:"description"`
	Platform        model.DevicePlatform `json:"platform" binding:"required"`
	ScriptType      string               `json:"script_type" binding:"required"`
	ScriptContent   string               `json:"script_content" binding:"required"`
	AppPackage      string               `json:"app_package"`
	AppPath         string               `json:"app_path"`
	Priority        int8                 `json:"priority"`
	ParallelCount   int                  `json:"parallel_count"`
	TimeoutSeconds  int                  `json:"timeout_seconds"`
	MaxRetries      int                  `json:"max_retries"`
	EnableRecording bool                 `json:"enable_recording"`
	DeviceIDs       []uint64             `json:"device_ids"`
}

func (s *TaskService) Create(req *CreateTaskRequest, createdBy uint64) (*model.TestTask, error) {
	if req.ParallelCount <= 0 {
		req.ParallelCount = 1
	}
	if req.TimeoutSeconds <= 0 {
		req.TimeoutSeconds = 3600
	}
	if req.Priority <= 0 {
		req.Priority = 5
	}

	deviceIDsJSON := "[]"
	if len(req.DeviceIDs) > 0 {
		bytes, _ := json.Marshal(req.DeviceIDs)
		deviceIDsJSON = string(bytes)
	}

	task := model.TestTask{
		Name:            req.Name,
		Description:     req.Description,
		Platform:        req.Platform,
		ScriptType:      req.ScriptType,
		ScriptContent:   req.ScriptContent,
		AppPackage:      req.AppPackage,
		AppPath:         req.AppPath,
		Priority:        req.Priority,
		Status:          model.TaskPending,
		DeviceIDs:       deviceIDsJSON,
		ParallelCount:   req.ParallelCount,
		TimeoutSeconds:  req.TimeoutSeconds,
		MaxRetries:      req.MaxRetries,
		EnableRecording: req.EnableRecording,
		CreatedBy:       createdBy,
	}
	if err := s.db.Create(&task).Error; err != nil {
		return nil, err
	}
	return &task, nil
}

func (s *TaskService) GetByID(id uint64) (*model.TestTask, error) {
	var task model.TestTask
	if err := s.db.First(&task, id).Error; err != nil {
		return nil, err
	}
	return &task, nil
}

func (s *TaskService) List(page, pageSize int, status string, createdBy uint64) ([]model.TestTask, int64, error) {
	var tasks []model.TestTask
	var total int64
	q := s.db.Model(&model.TestTask{})
	if status != "" {
		q = q.Where("status = ?", status)
	}
	if createdBy > 0 {
		q = q.Where("created_by = ?", createdBy)
	}
	q.Count(&total)
	offset := (page - 1) * pageSize
	if err := q.Offset(offset).Limit(pageSize).Order("id DESC").Find(&tasks).Error; err != nil {
		return nil, 0, err
	}
	return tasks, total, nil
}

func (s *TaskService) UpdateStatus(id uint64, status model.TaskStatus) error {
	return s.db.Model(&model.TestTask{}).Where("id = ?", id).Update("status", status).Error
}

func (s *TaskService) Delete(id uint64) error {
	var task model.TestTask
	if err := s.db.First(&task, id).Error; err != nil {
		return err
	}
	if task.Status == model.TaskRunning {
		return gorm.ErrInvalidTransaction
	}
	return s.db.Delete(&task).Error
}

func (s *TaskService) GetExecutions(taskID uint64) ([]model.TaskExecution, error) {
	var executions []model.TaskExecution
	err := s.db.Preload("Device").Where("task_id = ?", taskID).Find(&executions).Error
	return executions, err
}

func (s *TaskService) GetLogs(taskID uint64, logType string) ([]model.ExecutionLog, error) {
	var logs []model.ExecutionLog
	q := s.db.Where("task_id = ?", taskID)
	if logType != "" {
		q = q.Where("log_type = ?", logType)
	}
	err := q.Order("created_at ASC").Find(&logs).Error
	return logs, err
}

func (s *TaskService) SubmitTask(ctx context.Context, taskID uint64) error {
	return s.db.Model(&model.TestTask{}).Where("id = ?", taskID).Update("status", model.TaskQueued).Error
}
