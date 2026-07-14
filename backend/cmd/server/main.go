package main

import (
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"github.com/atp-platform/backend/internal/config"
	"github.com/atp-platform/backend/internal/handler"
	"github.com/atp-platform/backend/internal/middleware"
	"github.com/atp-platform/backend/internal/pkg/database"
	redispkg "github.com/atp-platform/backend/internal/pkg/redis"
	"github.com/atp-platform/backend/internal/pkg/storage"
	"github.com/atp-platform/backend/internal/router"
	"github.com/atp-platform/backend/internal/service"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

func main() {
	logger, _ := zap.NewProduction()
	defer logger.Sync()

	cfg, err := config.Load()
	if err != nil {
		logger.Fatal("load config failed", zap.Error(err))
	}

	if cfg.Env == "production" {
		gin.SetMode(gin.ReleaseMode)
	}

	db, err := database.NewMySQL(cfg.MySQLDSN, logger)
	if err != nil {
		logger.Fatal("connect mysql failed", zap.Error(err))
	}

	redisClient, err := redispkg.NewClient(cfg.RedisAddr, cfg.RedisPass)
	if err != nil {
		logger.Fatal("connect redis failed", zap.Error(err))
	}
	defer redisClient.Close()

	minioStorage, err := storage.NewMinIOStorage(cfg.MinIO)
	if err != nil {
		logger.Fatal("connect minio failed", zap.Error(err))
	}

	authService := service.NewAuthService(db)
	deviceService := service.NewDeviceService(db, redisClient.RDB())
	taskService := service.NewTaskService(db)
	recordingService := service.NewRecordingService(db, minioStorage)
	reportService := service.NewReportService(db)
	controlPoolService := service.NewControlPoolService(db)
	schedulerService := service.NewSchedulerService(db, redisClient.RDB(), deviceService, taskService, cfg.Scheduler, logger)

	authMW := middleware.NewAuthMiddleware(authService, cfg.JWTSecret)

	handlers := &router.Handlers{
		Auth:        handler.NewAuthHandler(authService, authMW),
		Device:      handler.NewDeviceHandler(deviceService, authService),
		Task:        handler.NewTaskHandler(taskService, schedulerService, authService),
		Recording:   handler.NewRecordingHandler(recordingService),
		Report:      handler.NewReportHandler(reportService),
		ControlPool: handler.NewControlPoolHandler(controlPoolService),
		AuthMW:      authMW,
	}

	schedulerService.StartWorker()
	defer schedulerService.Stop()

	r := gin.New()
	router.Setup(r, handlers)

	addr := fmt.Sprintf(":%d", cfg.ServerPort)
	logger.Info("server starting", zap.String("addr", addr))

	go func() {
		if err := r.Run(addr); err != nil {
			logger.Fatal("server run failed", zap.Error(err))
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	logger.Info("server shutting down")
}
