package service

import (
	"fmt"

	"github.com/atp-platform/backend/internal/model"
	"gorm.io/gorm"
)

type ReportService struct {
	db *gorm.DB
}

func NewReportService(db *gorm.DB) *ReportService {
	return &ReportService{db: db}
}

func (s *ReportService) GetByTaskID(taskID uint64) (*model.TestReport, error) {
	var report model.TestReport
	if err := s.db.Preload("Task").Where("task_id = ?", taskID).First(&report).Error; err != nil {
		return nil, err
	}
	return &report, nil
}

func (s *ReportService) List(page, pageSize int) ([]model.TestReport, int64, error) {
	var reports []model.TestReport
	var total int64
	s.db.Model(&model.TestReport{}).Count(&total)
	offset := (page - 1) * pageSize
	err := s.db.Preload("Task").Offset(offset).Limit(pageSize).Order("id DESC").Find(&reports).Error
	return reports, total, err
}

func (s *ReportService) GetDetail(taskID uint64) (map[string]interface{}, error) {
	report, err := s.GetByTaskID(taskID)
	if err != nil {
		return nil, err
	}

	var executions []model.TaskExecution
	s.db.Preload("Device").Where("task_id = ?", taskID).Find(&executions)

	var recordings []model.RecordingResource
	s.db.Where("task_id = ?", taskID).Find(&recordings)

	var logs []model.ExecutionLog
	s.db.Where("task_id = ?", taskID).Order("created_at ASC").Find(&logs)

	return map[string]interface{}{
		"report":     report,
		"executions": executions,
		"recordings": recordings,
		"logs":       logs,
	}, nil
}

func (s *ReportService) ExportPDF(taskID uint64) (string, error) {
	report, err := s.GetByTaskID(taskID)
	if err != nil {
		return "", err
	}
	pdfPath := fmt.Sprintf("reports/%d/report_%d.pdf", taskID, report.ID)
	s.db.Model(report).Update("pdf_path", pdfPath)
	return pdfPath, nil
}

func (s *ReportService) GetDashboardStats() (map[string]interface{}, error) {
	var totalDevices, onlineDevices, busyDevices int64
	var totalTasks, runningTasks, successTasks, failedTasks int64

	s.db.Model(&model.Device{}).Count(&totalDevices)
	s.db.Model(&model.Device{}).Where("status = ?", model.DeviceOnline).Count(&onlineDevices)
	s.db.Model(&model.Device{}).Where("status = ?", model.DeviceBusy).Count(&busyDevices)
	s.db.Model(&model.TestTask{}).Count(&totalTasks)
	s.db.Model(&model.TestTask{}).Where("status = ?", model.TaskRunning).Count(&runningTasks)
	s.db.Model(&model.TestTask{}).Where("status = ?", model.TaskSuccess).Count(&successTasks)
	s.db.Model(&model.TestTask{}).Where("status = ?", model.TaskFailed).Count(&failedTasks)

	passRate := float64(0)
	if successTasks+failedTasks > 0 {
		passRate = float64(successTasks) / float64(successTasks+failedTasks) * 100
	}

	return map[string]interface{}{
		"devices": map[string]int64{
			"total":  totalDevices,
			"online": onlineDevices,
			"busy":   busyDevices,
		},
		"tasks": map[string]int64{
			"total":   totalTasks,
			"running": runningTasks,
			"success": successTasks,
			"failed":  failedTasks,
		},
		"pass_rate": passRate,
	}, nil
}
