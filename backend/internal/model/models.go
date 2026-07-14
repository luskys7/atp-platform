package model

import (
	"time"

	"gorm.io/gorm"
)

type UserRole string

const (
	RoleSuperAdmin        UserRole = "super_admin"
	RoleTestAdmin         UserRole = "test_admin"
	RoleTester            UserRole = "tester"
	RoleDeveloperReadonly UserRole = "developer_readonly"
)

type User struct {
	ID           uint64     `gorm:"primaryKey" json:"id"`
	Username     string     `gorm:"size:64;uniqueIndex" json:"username"`
	PasswordHash string     `gorm:"size:255" json:"-"`
	DisplayName  string     `gorm:"size:128" json:"display_name"`
	Role         UserRole   `gorm:"size:32" json:"role"`
	Status       int8       `gorm:"default:1" json:"status"`
	LastLoginAt  *time.Time `json:"last_login_at"`
	CreatedAt    time.Time  `json:"created_at"`
	UpdatedAt    time.Time  `json:"updated_at"`
}

func (User) TableName() string { return "users" }

type AuditLog struct {
	ID           uint64    `gorm:"primaryKey" json:"id"`
	UserID       uint64    `json:"user_id"`
	Action       string    `gorm:"size:64" json:"action"`
	ResourceType string    `gorm:"size:64" json:"resource_type"`
	ResourceID   string    `gorm:"size:128" json:"resource_id"`
	Detail       string    `gorm:"type:json" json:"detail"`
	IP           string    `gorm:"size:64" json:"ip"`
	CreatedAt    time.Time `json:"created_at"`
}

func (AuditLog) TableName() string { return "audit_logs" }

type DevicePlatform string

const (
	PlatformAndroid DevicePlatform = "android"
	PlatformIOS     DevicePlatform = "ios"
)

type DeviceStatus string

const (
	DeviceOnline      DeviceStatus = "online"
	DeviceOffline     DeviceStatus = "offline"
	DeviceBusy        DeviceStatus = "busy"
	DeviceMaintenance DeviceStatus = "maintenance"
	DeviceError       DeviceStatus = "error"
)

type Device struct {
	ID              uint64         `gorm:"primaryKey" json:"id"`
	SerialNumber    string         `gorm:"size:128;uniqueIndex" json:"serial_number"`
	Name            string         `gorm:"size:128" json:"name"`
	Platform        DevicePlatform `gorm:"size:16" json:"platform"`
	OSVersion       string         `gorm:"size:32" json:"os_version"`
	Model           string         `gorm:"size:128" json:"model"`
	Status          DeviceStatus   `gorm:"size:16" json:"status"`
	AgentHost       string         `gorm:"size:128" json:"agent_host"`
	AgentPort       int            `json:"agent_port"`
	BatteryLevel    uint8          `json:"battery_level"`
	ScreenWidth     int            `json:"screen_width"`
	ScreenHeight    int            `json:"screen_height"`
	WDAPort         int            `json:"wda_port"`
	ADBPort         int            `json:"adb_port"`
	LastHeartbeatAt *time.Time     `json:"last_heartbeat_at"`
	LockedByTaskID  *uint64        `json:"locked_by_task_id"`
	LockExpiresAt   *time.Time     `json:"lock_expires_at"`
	IsWhitelisted   bool           `json:"is_whitelisted"`
	CreatedAt       time.Time      `json:"created_at"`
	UpdatedAt       time.Time      `json:"updated_at"`
}

func (Device) TableName() string { return "devices" }

type DeviceWhitelist struct {
	ID           uint64         `gorm:"primaryKey" json:"id"`
	SerialNumber string         `gorm:"size:128;uniqueIndex" json:"serial_number"`
	Platform     DevicePlatform `gorm:"size:16" json:"platform"`
	Remark       string         `gorm:"size:255" json:"remark"`
	CreatedBy    uint64         `json:"created_by"`
	CreatedAt    time.Time      `json:"created_at"`
}

func (DeviceWhitelist) TableName() string { return "device_whitelist" }

type TaskStatus string

const (
	TaskPending   TaskStatus = "pending"
	TaskQueued    TaskStatus = "queued"
	TaskRunning   TaskStatus = "running"
	TaskSuccess   TaskStatus = "success"
	TaskFailed    TaskStatus = "failed"
	TaskCancelled TaskStatus = "cancelled"
	TaskTimeout   TaskStatus = "timeout"
)

type TestTask struct {
	ID              uint64         `gorm:"primaryKey" json:"id"`
	Name            string         `gorm:"size:256" json:"name"`
	Description     string         `gorm:"type:text" json:"description"`
	Platform        DevicePlatform `gorm:"size:16" json:"platform"`
	ScriptType      string         `gorm:"size:16" json:"script_type"`
	ScriptContent   string         `gorm:"type:longtext" json:"script_content"`
	ScriptPath      string         `gorm:"size:512" json:"script_path"`
	AppPackage      string         `gorm:"size:256" json:"app_package"`
	AppPath         string         `gorm:"size:512" json:"app_path"`
	Priority        int8           `gorm:"default:5" json:"priority"`
	Status          TaskStatus     `gorm:"size:16" json:"status"`
	DeviceIDs       string         `gorm:"type:json" json:"device_ids"`
	ParallelCount   int            `gorm:"default:1" json:"parallel_count"`
	TimeoutSeconds  int            `gorm:"default:3600" json:"timeout_seconds"`
	RetryCount      int            `json:"retry_count"`
	MaxRetries      int            `json:"max_retries"`
	EnableRecording bool           `gorm:"default:true" json:"enable_recording"`
	CreatedBy       uint64         `json:"created_by"`
	StartedAt       *time.Time     `json:"started_at"`
	FinishedAt      *time.Time     `json:"finished_at"`
	ErrorCode       *string        `gorm:"size:16" json:"error_code"`
	ErrorMessage    *string        `gorm:"type:text" json:"error_message"`
	CreatedAt       time.Time      `json:"created_at"`
	UpdatedAt       time.Time      `json:"updated_at"`
}

func (TestTask) TableName() string { return "test_tasks" }

type TaskExecution struct {
	ID            uint64     `gorm:"primaryKey" json:"id"`
	TaskID        uint64     `json:"task_id"`
	DeviceID      uint64     `json:"device_id"`
	Status        TaskStatus `gorm:"size:16" json:"status"`
	StartedAt     *time.Time `json:"started_at"`
	FinishedAt    *time.Time `json:"finished_at"`
	ResultSummary string     `gorm:"type:text" json:"result_summary"`
	ErrorCode     *string    `gorm:"size:16" json:"error_code"`
	ErrorMessage  *string    `gorm:"type:text" json:"error_message"`
	CreatedAt     time.Time  `json:"created_at"`
	UpdatedAt     time.Time  `json:"updated_at"`
	Device        *Device    `gorm:"foreignKey:DeviceID" json:"device,omitempty"`
}

func (TaskExecution) TableName() string { return "task_executions" }

type RecordingStatus string

const (
	RecordingActive    RecordingStatus = "recording"
	RecordingUploading RecordingStatus = "uploading"
	RecordingReady     RecordingStatus = "ready"
	RecordingFailed    RecordingStatus = "failed"
)

type RecordingResource struct {
	ID              uint64          `gorm:"primaryKey" json:"id"`
	TaskID          uint64          `json:"task_id"`
	ExecutionID     *uint64         `json:"execution_id"`
	DeviceID        uint64          `json:"device_id"`
	FileName        string          `gorm:"size:256" json:"file_name"`
	FilePath        string          `gorm:"size:512" json:"file_path"`
	FileSize        uint64          `json:"file_size"`
	DurationSeconds int             `json:"duration_seconds"`
	Format          string          `gorm:"size:16" json:"format"`
	WatermarkHash   string          `gorm:"size:64" json:"watermark_hash"`
	Status          RecordingStatus `gorm:"size:16" json:"status"`
	CreatedAt       time.Time       `json:"created_at"`
	UpdatedAt       time.Time       `json:"updated_at"`
}

func (RecordingResource) TableName() string { return "recording_resources" }

type ExecutionLog struct {
	ID          uint64    `gorm:"primaryKey" json:"id"`
	TaskID      uint64    `json:"task_id"`
	ExecutionID *uint64   `json:"execution_id"`
	DeviceID    *uint64   `json:"device_id"`
	LogType     string    `gorm:"size:16" json:"log_type"`
	Level       string    `gorm:"size:16" json:"level"`
	Message     string    `gorm:"type:text" json:"message"`
	StackTrace  string    `gorm:"type:text" json:"stack_trace"`
	CreatedAt   time.Time `json:"created_at"`
}

func (ExecutionLog) TableName() string { return "execution_logs" }

type TestReport struct {
	ID              uint64    `gorm:"primaryKey" json:"id"`
	TaskID          uint64    `gorm:"uniqueIndex" json:"task_id"`
	Title           string    `gorm:"size:256" json:"title"`
	TotalExecutions int       `json:"total_executions"`
	SuccessCount    int       `json:"success_count"`
	FailedCount     int       `json:"failed_count"`
	PassRate        float64   `gorm:"type:decimal(5,2)" json:"pass_rate"`
	Summary         string    `gorm:"type:text" json:"summary"`
	PDFPath         string    `gorm:"size:512" json:"pdf_path"`
	CreatedAt       time.Time `json:"created_at"`
	UpdatedAt       time.Time `json:"updated_at"`
	Task            *TestTask `gorm:"foreignKey:TaskID" json:"task,omitempty"`
}

func (TestReport) TableName() string { return "test_reports" }

// P1 控件池模型
type ControlPool struct {
	ID            uint64         `gorm:"primaryKey" json:"id"`
	PoolKey       string         `gorm:"size:128;uniqueIndex" json:"pool_key"`
	AppPackage    string         `gorm:"size:256" json:"app_package"`
	PageName      string         `gorm:"size:256" json:"page_name"`
	ElementName   string         `gorm:"size:256" json:"element_name"`
	Platform      DevicePlatform `gorm:"size:16" json:"platform"`
	LocatorType   string         `gorm:"size:16" json:"locator_type"`
	LocatorValue  string         `gorm:"type:text" json:"locator_value"`
	FeatureVector string         `gorm:"type:json" json:"feature_vector"`
	HitCount      uint           `json:"hit_count"`
	VersionTag    string         `gorm:"size:64" json:"version_tag"`
	Status        string         `gorm:"size:16" json:"status"`
	CreatedAt     time.Time      `json:"created_at"`
	UpdatedAt     time.Time      `json:"updated_at"`
}

func (ControlPool) TableName() string { return "control_pools" }

type PrivateControlBinding struct {
	ID            uint64    `gorm:"primaryKey" json:"id"`
	TaskID        uint64    `json:"task_id"`
	StepIndex     int       `json:"step_index"`
	ElementName   string    `gorm:"size:256" json:"element_name"`
	LocatorType   string    `gorm:"size:16" json:"locator_type"`
	LocatorValue  string    `gorm:"type:text" json:"locator_value"`
	PoolID        *uint64   `json:"pool_id"`
	IsIsolated    bool      `gorm:"default:true" json:"is_isolated"`
	CreatedBy     uint64    `json:"created_by"`
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`
}

func (PrivateControlBinding) TableName() string { return "private_control_bindings" }

func AutoMigrate(db *gorm.DB) error {
	return db.AutoMigrate(
		&User{},
		&AuditLog{},
		&Device{},
		&DeviceWhitelist{},
		&TestTask{},
		&TaskExecution{},
		&RecordingResource{},
		&ExecutionLog{},
		&TestReport{},
		&ControlPool{},
		&PrivateControlBinding{},
	)
}
