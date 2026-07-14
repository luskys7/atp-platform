package service

import (
	"context"
	"crypto/sha256"
	"fmt"
	"io"
	"time"

	apperr "github.com/atp-platform/backend/internal/pkg/errors"
	"github.com/atp-platform/backend/internal/model"
	"github.com/atp-platform/backend/internal/pkg/storage"
	"gorm.io/gorm"
)

type RecordingService struct {
	db      *gorm.DB
	storage *storage.MinIOStorage
}

func NewRecordingService(db *gorm.DB, storage *storage.MinIOStorage) *RecordingService {
	return &RecordingService{db: db, storage: storage}
}

func (s *RecordingService) StartRecording(taskID, deviceID uint64, executionID *uint64) (*model.RecordingResource, error) {
	rec := model.RecordingResource{
		TaskID:      taskID,
		ExecutionID: executionID,
		DeviceID:    deviceID,
		FileName:    fmt.Sprintf("task_%d_device_%d_%d.mp4", taskID, deviceID, time.Now().Unix()),
		FilePath:    fmt.Sprintf("recordings/%d/%d/%d.mp4", taskID, deviceID, time.Now().Unix()),
		Format:      "mp4",
		Status:      model.RecordingActive,
	}
	if err := s.db.Create(&rec).Error; err != nil {
		return nil, apperr.New(apperr.E2001, 500)
	}
	return &rec, nil
}

func (s *RecordingService) UploadRecording(ctx context.Context, recordingID uint64, reader io.Reader, size int64) error {
	var rec model.RecordingResource
	if err := s.db.First(&rec, recordingID).Error; err != nil {
		return err
	}

	s.db.Model(&rec).Update("status", model.RecordingUploading)

	uploadCtx, cancel := context.WithTimeout(ctx, 5*time.Minute)
	defer cancel()

	path, err := s.storage.Upload(uploadCtx, rec.FilePath, reader, size, "video/mp4")
	if err != nil {
		s.db.Model(&rec).Update("status", model.RecordingFailed)
		return apperr.New(apperr.E2002, 500)
	}

	hash := sha256.Sum256([]byte(path + rec.FileName))
	watermark := fmt.Sprintf("%x", hash[:16])

	return s.db.Model(&rec).Updates(map[string]interface{}{
		"file_path":      path,
		"file_size":      size,
		"watermark_hash": watermark,
		"status":         model.RecordingReady,
	}).Error
}

func (s *RecordingService) GetByID(id uint64) (*model.RecordingResource, error) {
	var rec model.RecordingResource
	if err := s.db.First(&rec, id).Error; err != nil {
		return nil, err
	}
	return &rec, nil
}

func (s *RecordingService) ListByTask(taskID uint64) ([]model.RecordingResource, error) {
	var list []model.RecordingResource
	err := s.db.Where("task_id = ?", taskID).Order("created_at DESC").Find(&list).Error
	return list, err
}

func (s *RecordingService) List(page, pageSize int, taskID, deviceID uint64) ([]model.RecordingResource, int64, error) {
	var list []model.RecordingResource
	var total int64
	q := s.db.Model(&model.RecordingResource{})
	if taskID > 0 {
		q = q.Where("task_id = ?", taskID)
	}
	if deviceID > 0 {
		q = q.Where("device_id = ?", deviceID)
	}
	q.Count(&total)
	offset := (page - 1) * pageSize
	err := q.Offset(offset).Limit(pageSize).Order("id DESC").Find(&list).Error
	return list, total, err
}

func (s *RecordingService) GetPlaybackURL(ctx context.Context, id uint64) (string, error) {
	rec, err := s.GetByID(id)
	if err != nil {
		return "", err
	}
	if rec.Status != model.RecordingReady {
		return "", fmt.Errorf("录屏尚未就绪")
	}
	return s.storage.GetPresignedURL(ctx, rec.FilePath, 1*time.Hour)
}

func (s *RecordingService) Delete(id uint64) error {
	rec, err := s.GetByID(id)
	if err != nil {
		return err
	}
	ctx := context.Background()
	_ = s.storage.Delete(ctx, rec.FilePath)
	return s.db.Delete(rec).Error
}
