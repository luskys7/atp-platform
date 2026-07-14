package service

import (
	"context"
	"fmt"
	"time"

	apperr "github.com/atp-platform/backend/internal/pkg/errors"
	"github.com/atp-platform/backend/internal/model"
	redispkg "github.com/atp-platform/backend/internal/pkg/redis"
	"github.com/go-redis/redis/v8"
	"gorm.io/gorm"
)

type DeviceService struct {
	db    *gorm.DB
	redis *redis.Client
}

func NewDeviceService(db *gorm.DB, redisClient *redis.Client) *DeviceService {
	return &DeviceService{db: db, redis: redisClient}
}

type DeviceRegisterRequest struct {
	SerialNumber string               `json:"serial_number" binding:"required"`
	Name         string               `json:"name"`
	Platform     model.DevicePlatform `json:"platform" binding:"required"`
	OSVersion    string               `json:"os_version"`
	Model        string               `json:"model"`
	AgentHost    string               `json:"agent_host"`
	AgentPort    int                  `json:"agent_port"`
	ScreenWidth  int                  `json:"screen_width"`
	ScreenHeight int                  `json:"screen_height"`
	WDAPort      int                  `json:"wda_port"`
	ADBPort      int                  `json:"adb_port"`
	BatteryLevel uint8                `json:"battery_level"`
}

func (s *DeviceService) Register(req *DeviceRegisterRequest) (*model.Device, error) {
	var whitelist model.DeviceWhitelist
	if err := s.db.Where("serial_number = ?", req.SerialNumber).First(&whitelist).Error; err != nil {
		if err == gorm.ErrRecordNotFound {
			return nil, apperr.New(apperr.E1001, 403)
		}
		return nil, err
	}

	var device model.Device
	err := s.db.Where("serial_number = ?", req.SerialNumber).First(&device).Error
	now := time.Now()

	if err == gorm.ErrRecordNotFound {
		device = model.Device{
			SerialNumber:    req.SerialNumber,
			Name:            req.Name,
			Platform:        req.Platform,
			OSVersion:       req.OSVersion,
			Model:           req.Model,
			Status:          model.DeviceOnline,
			AgentHost:       req.AgentHost,
			AgentPort:       req.AgentPort,
			ScreenWidth:     req.ScreenWidth,
			ScreenHeight:    req.ScreenHeight,
			WDAPort:         req.WDAPort,
			ADBPort:         req.ADBPort,
			BatteryLevel:    req.BatteryLevel,
			LastHeartbeatAt: &now,
			IsWhitelisted:   true,
		}
		if err := s.db.Create(&device).Error; err != nil {
			return nil, err
		}
	} else if err != nil {
		return nil, err
	} else {
		updates := map[string]interface{}{
			"name":              req.Name,
			"os_version":        req.OSVersion,
			"model":             req.Model,
			"status":            model.DeviceOnline,
			"agent_host":        req.AgentHost,
			"agent_port":        req.AgentPort,
			"screen_width":      req.ScreenWidth,
			"screen_height":     req.ScreenHeight,
			"wda_port":          req.WDAPort,
			"adb_port":          req.ADBPort,
			"battery_level":     req.BatteryLevel,
			"last_heartbeat_at": now,
			"is_whitelisted":    true,
		}
		s.db.Model(&device).Updates(updates)
		s.db.First(&device, device.ID)
	}
	return &device, nil
}

func (s *DeviceService) Heartbeat(serialNumber string, batteryLevel uint8) error {
	now := time.Now()
	result := s.db.Model(&model.Device{}).
		Where("serial_number = ?", serialNumber).
		Updates(map[string]interface{}{
			"last_heartbeat_at": now,
			"battery_level":     batteryLevel,
			"status":            model.DeviceOnline,
		})
	if result.RowsAffected == 0 {
		return apperr.New(apperr.E1001, 403)
	}
	return result.Error
}

func (s *DeviceService) List(page, pageSize int, platform, status string) ([]model.Device, int64, error) {
	var devices []model.Device
	var total int64
	q := s.db.Model(&model.Device{})
	if platform != "" {
		q = q.Where("platform = ?", platform)
	}
	if status != "" {
		q = q.Where("status = ?", status)
	}
	q.Count(&total)
	offset := (page - 1) * pageSize
	if err := q.Offset(offset).Limit(pageSize).Order("id DESC").Find(&devices).Error; err != nil {
		return nil, 0, err
	}
	return devices, total, nil
}

func (s *DeviceService) GetByID(id uint64) (*model.Device, error) {
	var device model.Device
	if err := s.db.First(&device, id).Error; err != nil {
		return nil, err
	}
	return &device, nil
}

func (s *DeviceService) UpdateStatus(id uint64, status model.DeviceStatus) error {
	return s.db.Model(&model.Device{}).Where("id = ?", id).Update("status", status).Error
}

func (s *DeviceService) Delete(id uint64) error {
	var device model.Device
	if err := s.db.First(&device, id).Error; err != nil {
		return err
	}
	if device.LockedByTaskID != nil {
		return apperr.New(apperr.E1002, 409)
	}
	return s.db.Delete(&device).Error
}

func (s *DeviceService) AddWhitelist(serialNumber string, platform model.DevicePlatform, remark string, createdBy uint64) error {
	entry := model.DeviceWhitelist{
		SerialNumber: serialNumber,
		Platform:     platform,
		Remark:       remark,
		CreatedBy:    createdBy,
	}
	return s.db.Create(&entry).Error
}

func (s *DeviceService) ListWhitelist(page, pageSize int) ([]model.DeviceWhitelist, int64, error) {
	var list []model.DeviceWhitelist
	var total int64
	s.db.Model(&model.DeviceWhitelist{}).Count(&total)
	offset := (page - 1) * pageSize
	err := s.db.Offset(offset).Limit(pageSize).Order("id DESC").Find(&list).Error
	return list, total, err
}

func (s *DeviceService) RemoveWhitelist(id uint64) error {
	return s.db.Delete(&model.DeviceWhitelist{}, id).Error
}

func (s *DeviceService) GetAvailableDevices(platform model.DevicePlatform, count int) ([]model.Device, error) {
	var devices []model.Device
	q := s.db.Where("status = ? AND is_whitelisted = ?", model.DeviceOnline, true)
	if platform != "" && platform != "both" {
		q = q.Where("platform = ?", platform)
	}
	if err := q.Limit(count).Find(&devices).Error; err != nil {
		return nil, err
	}
	return devices, nil
}

func (s *DeviceService) TryLockDevice(ctx context.Context, deviceID, taskID uint64, ttl time.Duration) error {
	lockKey := redispkg.DeviceLockPrefix + fmt.Sprintf("%d", deviceID)
	ok, err := s.redis.SetNX(ctx, lockKey, taskID, ttl).Result()
	if err != nil {
		return err
	}
	if !ok {
		return apperr.New(apperr.E1002, 409)
	}
	expiresAt := time.Now().Add(ttl)
	return s.db.Model(&model.Device{}).Where("id = ?", deviceID).Updates(map[string]interface{}{
		"status":            model.DeviceBusy,
		"locked_by_task_id": taskID,
		"lock_expires_at":   expiresAt,
	}).Error
}

func (s *DeviceService) ReleaseLock(ctx context.Context, deviceID uint64) error {
	lockKey := redispkg.DeviceLockPrefix + fmt.Sprintf("%d", deviceID)
	s.redis.Del(ctx, lockKey)
	return s.db.Model(&model.Device{}).Where("id = ?", deviceID).Updates(map[string]interface{}{
		"status":            model.DeviceOnline,
		"locked_by_task_id": nil,
		"lock_expires_at":   nil,
	}).Error
}

func (s *DeviceService) RenewLock(ctx context.Context, deviceID, taskID uint64, ttl time.Duration) error {
	lockKey := redispkg.DeviceLockPrefix + fmt.Sprintf("%d", deviceID)
	val, err := s.redis.Get(ctx, lockKey).Uint64()
	if err != nil || val != taskID {
		return apperr.New(apperr.E1002, 409)
	}
	s.redis.Expire(ctx, lockKey, ttl)
	expiresAt := time.Now().Add(ttl)
	return s.db.Model(&model.Device{}).Where("id = ?", deviceID).Update("lock_expires_at", expiresAt).Error
}

func (s *DeviceService) MarkOfflineStale(threshold time.Duration) (int64, error) {
	cutoff := time.Now().Add(-threshold)
	result := s.db.Model(&model.Device{}).
		Where("last_heartbeat_at < ? AND status != ?", cutoff, model.DeviceMaintenance).
		Updates(map[string]interface{}{
			"status":            model.DeviceOffline,
			"locked_by_task_id": nil,
			"lock_expires_at":   nil,
		})
	return result.RowsAffected, result.Error
}
