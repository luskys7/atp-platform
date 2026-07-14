package router

import (
	"github.com/atp-platform/backend/internal/handler"
	"github.com/atp-platform/backend/internal/middleware"
	"github.com/atp-platform/backend/internal/model"
	"github.com/gin-gonic/gin"
)

type Handlers struct {
	Auth        *handler.AuthHandler
	Device      *handler.DeviceHandler
	Task        *handler.TaskHandler
	Recording   *handler.RecordingHandler
	Report      *handler.ReportHandler
	ControlPool *handler.ControlPoolHandler
	AuthMW      *middleware.AuthMiddleware
}

func Setup(r *gin.Engine, h *Handlers) {
	r.Use(gin.Recovery())
	r.Use(corsMiddleware())

	r.GET("/health", handler.HealthCheck)
	r.GET("/api/health", handler.HealthCheck)

	// 公开接口
	api := r.Group("/api/v1")
	{
		api.POST("/auth/login", h.Auth.Login)

		// Agent 上报接口（设备注册/心跳）
		api.POST("/agent/devices/register", h.Device.Register)
		api.POST("/agent/devices/heartbeat", h.Device.Heartbeat)
	}

	// 需鉴权接口
	auth := api.Group("")
	auth.Use(h.AuthMW.JWTAuth())
	{
		// 用户
		auth.GET("/auth/profile", h.Auth.Profile)
		auth.PUT("/auth/profile", h.Auth.UpdateProfile)
		auth.PUT("/auth/password", h.Auth.ChangePassword)

		// 仪表盘
		auth.GET("/dashboard", h.Report.Dashboard)

		// 设备管理
		devices := auth.Group("/devices")
		{
			devices.GET("", h.Device.List)
			devices.GET("/:id", h.Device.Get)
			devices.PUT("/:id/status", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin), h.Device.UpdateStatus)
			devices.DELETE("/:id", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin), h.Device.Delete)

			devices.GET("/whitelist", h.Device.ListWhitelist)
			devices.POST("/whitelist", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin), h.Device.AddWhitelist)
			devices.DELETE("/whitelist/:id", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin), h.Device.RemoveWhitelist)
		}

		// 测试任务
		tasks := auth.Group("/tasks")
		{
			tasks.GET("", h.Task.List)
			tasks.POST("", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin, model.RoleTester), h.Task.Create)
			tasks.GET("/:id", h.Task.Get)
			tasks.POST("/:id/submit", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin, model.RoleTester), h.Task.Submit)
			tasks.POST("/:id/cancel", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin, model.RoleTester), h.Task.Cancel)
			tasks.DELETE("/:id", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin), h.Task.Delete)
			tasks.GET("/:id/executions", h.Task.GetExecutions)
			tasks.GET("/:id/logs", h.Task.GetLogs)
		}

		// 录屏
		recordings := auth.Group("/recordings")
		{
			recordings.GET("", h.Recording.List)
			recordings.GET("/:id/playback", h.Recording.GetPlaybackURL)
			recordings.DELETE("/:id", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin), h.Recording.Delete)
		}

		// 测试报告
		reports := auth.Group("/reports")
		{
			reports.GET("", h.Report.List)
			reports.GET("/:task_id", h.Report.GetDetail)
			reports.GET("/:task_id/export", h.Report.ExportPDF)
		}

		// P1 控件池
		controls := auth.Group("/controls")
		{
			controls.GET("/pool", h.ControlPool.List)
			controls.POST("/pool", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin), h.ControlPool.Create)
			controls.POST("/private-bindings", h.AuthMW.RequireRoles(model.RoleSuperAdmin, model.RoleTestAdmin, model.RoleTester), h.ControlPool.CreatePrivateBinding)
			controls.GET("/private-bindings/:task_id", h.ControlPool.GetPrivateBindings)
		}
	}
}

func corsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Authorization")
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	}
}
