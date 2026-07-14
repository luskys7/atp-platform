package handler

import (
	"net/http"
	"strconv"

	"github.com/atp-platform/backend/internal/middleware"
	"github.com/atp-platform/backend/internal/model"
	"github.com/atp-platform/backend/internal/pkg/response"
	"github.com/atp-platform/backend/internal/service"
	"github.com/gin-gonic/gin"
)

type AuthHandler struct {
	authService *service.AuthService
	authMW      *middleware.AuthMiddleware
}

func NewAuthHandler(authService *service.AuthService, authMW *middleware.AuthMiddleware) *AuthHandler {
	return &AuthHandler{authService: authService, authMW: authMW}
}

func (h *AuthHandler) Login(c *gin.Context) {
	var req service.LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	user, err := h.authService.Login(req.Username, req.Password)
	if err != nil {
		response.Unauthorized(c, err.Error())
		return
	}
	token, expiresAt, err := h.authMW.GenerateToken(user)
	if err != nil {
		response.Fail(c, err)
		return
	}
	user.PasswordHash = ""
	response.OK(c, gin.H{
		"token":      token,
		"expires_at": expiresAt,
		"user":       user,
	})
}

func (h *AuthHandler) Profile(c *gin.Context) {
	userID := middleware.GetUserID(c)
	user, err := h.authService.GetUserByID(userID)
	if err != nil {
		response.NotFound(c, "用户不存在")
		return
	}
	user.PasswordHash = ""
	response.OK(c, user)
}

func (h *AuthHandler) UpdateProfile(c *gin.Context) {
	var req struct {
		DisplayName string `json:"display_name"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	userID := middleware.GetUserID(c)
	if err := h.authService.UpdateProfile(userID, req.DisplayName); err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, nil)
}

func (h *AuthHandler) ChangePassword(c *gin.Context) {
	var req struct {
		OldPassword string `json:"old_password" binding:"required"`
		NewPassword string `json:"new_password" binding:"required,min=6"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	userID := middleware.GetUserID(c)
	if err := h.authService.ChangePassword(userID, req.OldPassword, req.NewPassword); err != nil {
		response.BadRequest(c, err.Error())
		return
	}
	response.OK(c, nil)
}

type DeviceHandler struct {
	deviceService *service.DeviceService
	authService   *service.AuthService
}

func NewDeviceHandler(deviceService *service.DeviceService, authService *service.AuthService) *DeviceHandler {
	return &DeviceHandler{deviceService: deviceService, authService: authService}
}

func (h *DeviceHandler) Register(c *gin.Context) {
	var req service.DeviceRegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	device, err := h.deviceService.Register(&req)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Created(c, device)
}

func (h *DeviceHandler) Heartbeat(c *gin.Context) {
	var req struct {
		SerialNumber string `json:"serial_number" binding:"required"`
		BatteryLevel uint8  `json:"battery_level"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	if err := h.deviceService.Heartbeat(req.SerialNumber, req.BatteryLevel); err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, nil)
}

func (h *DeviceHandler) List(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	devices, total, err := h.deviceService.List(page, pageSize, c.Query("platform"), c.Query("status"))
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Page(c, devices, total, page, pageSize)
}

func (h *DeviceHandler) Get(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	device, err := h.deviceService.GetByID(id)
	if err != nil {
		response.NotFound(c, "设备不存在")
		return
	}
	response.OK(c, device)
}

func (h *DeviceHandler) UpdateStatus(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	var req struct {
		Status model.DeviceStatus `json:"status" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	if err := h.deviceService.UpdateStatus(id, req.Status); err != nil {
		response.Fail(c, err)
		return
	}
	h.authService.CreateAuditLog(middleware.GetUserID(c), "update_status", "device", c.Param("id"), string(req.Status), c.ClientIP())
	response.OK(c, nil)
}

func (h *DeviceHandler) Delete(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	if err := h.deviceService.Delete(id); err != nil {
		response.Fail(c, err)
		return
	}
	h.authService.CreateAuditLog(middleware.GetUserID(c), "delete", "device", c.Param("id"), "", c.ClientIP())
	response.OK(c, nil)
}

func (h *DeviceHandler) AddWhitelist(c *gin.Context) {
	var req struct {
		SerialNumber string               `json:"serial_number" binding:"required"`
		Platform     model.DevicePlatform `json:"platform" binding:"required"`
		Remark       string               `json:"remark"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	if err := h.deviceService.AddWhitelist(req.SerialNumber, req.Platform, req.Remark, middleware.GetUserID(c)); err != nil {
		response.Fail(c, err)
		return
	}
	response.Created(c, nil)
}

func (h *DeviceHandler) ListWhitelist(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	list, total, err := h.deviceService.ListWhitelist(page, pageSize)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Page(c, list, total, page, pageSize)
}

func (h *DeviceHandler) RemoveWhitelist(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	if err := h.deviceService.RemoveWhitelist(id); err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, nil)
}

type TaskHandler struct {
	taskService      *service.TaskService
	schedulerService *service.SchedulerService
	authService      *service.AuthService
}

func NewTaskHandler(taskService *service.TaskService, schedulerService *service.SchedulerService, authService *service.AuthService) *TaskHandler {
	return &TaskHandler{taskService: taskService, schedulerService: schedulerService, authService: authService}
}

func (h *TaskHandler) Create(c *gin.Context) {
	var req service.CreateTaskRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	task, err := h.taskService.Create(&req, middleware.GetUserID(c))
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Created(c, task)
}

func (h *TaskHandler) List(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	role := middleware.GetRole(c)
	createdBy := uint64(0)
	if role == model.RoleDeveloperReadonly || role == model.RoleTester {
		createdBy = middleware.GetUserID(c)
	}
	tasks, total, err := h.taskService.List(page, pageSize, c.Query("status"), createdBy)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Page(c, tasks, total, page, pageSize)
}

func (h *TaskHandler) Get(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	task, err := h.taskService.GetByID(id)
	if err != nil {
		response.NotFound(c, "任务不存在")
		return
	}
	response.OK(c, task)
}

func (h *TaskHandler) Submit(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	task, err := h.taskService.GetByID(id)
	if err != nil {
		response.NotFound(c, "任务不存在")
		return
	}
	if err := h.taskService.SubmitTask(c.Request.Context(), id); err != nil {
		response.Fail(c, err)
		return
	}
	if err := h.schedulerService.EnqueueTask(c.Request.Context(), id, task.Priority); err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, gin.H{"message": "任务已提交调度"})
}

func (h *TaskHandler) Cancel(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	if err := h.schedulerService.CancelTask(c.Request.Context(), id); err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, nil)
}

func (h *TaskHandler) Delete(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	if err := h.taskService.Delete(id); err != nil {
		response.BadRequest(c, "无法删除运行中的任务")
		return
	}
	h.authService.CreateAuditLog(middleware.GetUserID(c), "delete", "task", c.Param("id"), "", c.ClientIP())
	response.OK(c, nil)
}

func (h *TaskHandler) GetExecutions(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	executions, err := h.taskService.GetExecutions(id)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, executions)
}

func (h *TaskHandler) GetLogs(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	logs, err := h.taskService.GetLogs(id, c.Query("log_type"))
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, logs)
}

type RecordingHandler struct {
	recordingService *service.RecordingService
}

func NewRecordingHandler(recordingService *service.RecordingService) *RecordingHandler {
	return &RecordingHandler{recordingService: recordingService}
}

func (h *RecordingHandler) List(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	taskID, _ := strconv.ParseUint(c.Query("task_id"), 10, 64)
	deviceID, _ := strconv.ParseUint(c.Query("device_id"), 10, 64)
	list, total, err := h.recordingService.List(page, pageSize, taskID, deviceID)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Page(c, list, total, page, pageSize)
}

func (h *RecordingHandler) GetPlaybackURL(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	url, err := h.recordingService.GetPlaybackURL(c.Request.Context(), id)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, gin.H{"url": url})
}

func (h *RecordingHandler) Delete(c *gin.Context) {
	id, _ := strconv.ParseUint(c.Param("id"), 10, 64)
	if err := h.recordingService.Delete(id); err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, nil)
}

type ReportHandler struct {
	reportService *service.ReportService
}

func NewReportHandler(reportService *service.ReportService) *ReportHandler {
	return &ReportHandler{reportService: reportService}
}

func (h *ReportHandler) List(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	reports, total, err := h.reportService.List(page, pageSize)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Page(c, reports, total, page, pageSize)
}

func (h *ReportHandler) GetDetail(c *gin.Context) {
	taskID, _ := strconv.ParseUint(c.Param("task_id"), 10, 64)
	detail, err := h.reportService.GetDetail(taskID)
	if err != nil {
		response.NotFound(c, "报告不存在")
		return
	}
	response.OK(c, detail)
}

func (h *ReportHandler) ExportPDF(c *gin.Context) {
	taskID, _ := strconv.ParseUint(c.Param("task_id"), 10, 64)
	path, err := h.reportService.ExportPDF(taskID)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, gin.H{"pdf_path": path})
}

func (h *ReportHandler) Dashboard(c *gin.Context) {
	stats, err := h.reportService.GetDashboardStats()
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, stats)
}

type ControlPoolHandler struct {
	controlPoolService *service.ControlPoolService
}

func NewControlPoolHandler(controlPoolService *service.ControlPoolService) *ControlPoolHandler {
	return &ControlPoolHandler{controlPoolService: controlPoolService}
}

func (h *ControlPoolHandler) Create(c *gin.Context) {
	var req service.CreateControlPoolRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	pool, err := h.controlPoolService.Create(&req)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Created(c, pool)
}

func (h *ControlPoolHandler) List(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))
	list, total, err := h.controlPoolService.List(page, pageSize, c.Query("app_package"))
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Page(c, list, total, page, pageSize)
}

func (h *ControlPoolHandler) CreatePrivateBinding(c *gin.Context) {
	var req struct {
		TaskID       uint64 `json:"task_id" binding:"required"`
		StepIndex    int    `json:"step_index" binding:"required"`
		ElementName  string `json:"element_name" binding:"required"`
		LocatorType  string `json:"locator_type" binding:"required"`
		LocatorValue string `json:"locator_value" binding:"required"`
		PoolID       *uint64 `json:"pool_id"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		response.BadRequest(c, "参数错误")
		return
	}
	binding, err := h.controlPoolService.CreatePrivateBinding(
		req.TaskID, req.StepIndex, req.ElementName, req.LocatorType, req.LocatorValue, req.PoolID, middleware.GetUserID(c),
	)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.Created(c, binding)
}

func (h *ControlPoolHandler) GetPrivateBindings(c *gin.Context) {
	taskID, _ := strconv.ParseUint(c.Param("task_id"), 10, 64)
	bindings, err := h.controlPoolService.GetPrivateBindings(taskID)
	if err != nil {
		response.Fail(c, err)
		return
	}
	response.OK(c, bindings)
}

func HealthCheck(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"status": "ok", "service": "atp-platform"})
}
