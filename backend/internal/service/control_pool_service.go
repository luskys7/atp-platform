package service

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"

	apperr "github.com/atp-platform/backend/internal/pkg/errors"
	"github.com/atp-platform/backend/internal/model"
	"gorm.io/gorm"
)

type ControlPoolService struct {
	db *gorm.DB
}

func NewControlPoolService(db *gorm.DB) *ControlPoolService {
	return &ControlPoolService{db: db}
}

type CreateControlPoolRequest struct {
	AppPackage   string               `json:"app_package" binding:"required"`
	PageName     string               `json:"page_name"`
	ElementName  string               `json:"element_name" binding:"required"`
	Platform     model.DevicePlatform `json:"platform"`
	LocatorType  string               `json:"locator_type" binding:"required"`
	LocatorValue string               `json:"locator_value" binding:"required"`
	VersionTag   string               `json:"version_tag"`
}

func (s *ControlPoolService) generatePoolKey(appPackage, pageName, elementName string) string {
	raw := fmt.Sprintf("%s:%s:%s", appPackage, pageName, elementName)
	hash := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(hash[:16])
}

func (s *ControlPoolService) Create(req *CreateControlPoolRequest) (*model.ControlPool, error) {
	if req.LocatorValue == "" {
		return nil, apperr.New(apperr.E4003, 400)
	}

	poolKey := s.generatePoolKey(req.AppPackage, req.PageName, req.ElementName)

	var existing model.ControlPool
	if err := s.db.Where("pool_key = ?", poolKey).First(&existing).Error; err == nil {
		return nil, fmt.Errorf("控件池条目已存在")
	}

	pool := model.ControlPool{
		PoolKey:      poolKey,
		AppPackage:   req.AppPackage,
		PageName:     req.PageName,
		ElementName:  req.ElementName,
		Platform:     req.Platform,
		LocatorType:  req.LocatorType,
		LocatorValue: req.LocatorValue,
		VersionTag:   req.VersionTag,
		Status:       "active",
	}
	if err := s.db.Create(&pool).Error; err != nil {
		return nil, err
	}
	return &pool, nil
}

func (s *ControlPoolService) Lookup(appPackage, elementName string) (*model.ControlPool, error) {
	var pool model.ControlPool
	err := s.db.Where("app_package = ? AND element_name = ? AND status = ?", appPackage, elementName, "active").
		Order("hit_count DESC").First(&pool).Error
	if err != nil {
		return nil, apperr.New(apperr.E4001, 200)
	}
	s.db.Model(&pool).Update("hit_count", gorm.Expr("hit_count + 1"))
	return &pool, nil
}

func (s *ControlPoolService) List(page, pageSize int, appPackage string) ([]model.ControlPool, int64, error) {
	var list []model.ControlPool
	var total int64
	q := s.db.Model(&model.ControlPool{})
	if appPackage != "" {
		q = q.Where("app_package = ?", appPackage)
	}
	q.Count(&total)
	offset := (page - 1) * pageSize
	err := q.Offset(offset).Limit(pageSize).Order("hit_count DESC").Find(&list).Error
	return list, total, err
}

func (s *ControlPoolService) CreatePrivateBinding(taskID uint64, stepIndex int, elementName, locatorType, locatorValue string, poolID *uint64, createdBy uint64) (*model.PrivateControlBinding, error) {
	binding := model.PrivateControlBinding{
		TaskID:       taskID,
		StepIndex:    stepIndex,
		ElementName:  elementName,
		LocatorType:  locatorType,
		LocatorValue: locatorValue,
		PoolID:       poolID,
		IsIsolated:   true,
		CreatedBy:    createdBy,
	}
	if err := s.db.Create(&binding).Error; err != nil {
		return nil, apperr.New(apperr.E4002, 400)
	}
	return &binding, nil
}

func (s *ControlPoolService) GetPrivateBindings(taskID uint64) ([]model.PrivateControlBinding, error) {
	var bindings []model.PrivateControlBinding
	err := s.db.Where("task_id = ?", taskID).Order("step_index ASC").Find(&bindings).Error
	return bindings, err
}

func (s *ControlPoolService) ResolveLocator(taskID uint64, stepIndex int, elementName, appPackage string) (string, string, error) {
	var binding model.PrivateControlBinding
	err := s.db.Where("task_id = ? AND step_index = ? AND element_name = ? AND is_isolated = ?",
		taskID, stepIndex, elementName, true).First(&binding).Error
	if err == nil {
		return binding.LocatorType, binding.LocatorValue, nil
	}

	pool, err := s.Lookup(appPackage, elementName)
	if err != nil {
		return "id", "", err
	}
	return pool.LocatorType, pool.LocatorValue, nil
}
