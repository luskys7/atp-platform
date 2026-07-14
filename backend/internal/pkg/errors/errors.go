package errors

import "net/http"

// 平台统一错误码定义
const (
	// P0 设备错误 E1xxx
	E1001 = "E1001" // 设备未录入白名单
	E1002 = "E1002" // 设备分布式锁占用
	E1003 = "E1003" // iOS WDA授信失效

	// P0 录屏/存储错误 E2xxx
	E2001 = "E2001" // 录屏权限抢占失败
	E2002 = "E2002" // 视频分片上传超时
	E2003 = "E2003" // 存储空间不足

	// P0 任务错误 E3xxx
	E3001 = "E3001" // 任务队列溢出
	E3002 = "E3002" // 任务超时熔断

	// P1 控件错误 E4xxx
	E4001 = "E4001" // 控件池检索异常
	E4002 = "E4002" // 私有控件绑定失效
	E4003 = "E4003" // 控件脏数据
)

var errorMessages = map[string]string{
	E1001: "设备未录入白名单，禁止接入",
	E1002: "设备分布式锁占用，调度失败",
	E1003: "iOS WDA授信失效，连接中断",
	E2001: "设备录屏权限抢占失败",
	E2002: "视频分片上传超时",
	E2003: "存储空间不足，禁止启动任务",
	E3001: "任务队列溢出，拒绝新建任务",
	E3002: "任务超时熔断，强制终止",
	E4001: "控件池检索异常，自动降级原生定位",
	E4002: "私有控件绑定失效，隔离校验拦截",
	E4003: "控件脏数据，禁止入库",
}

type AppError struct {
	Code       string `json:"code"`
	Message    string `json:"message"`
	HTTPStatus int    `json:"-"`
}

func (e *AppError) Error() string {
	return e.Message
}

func New(code string, httpStatus int) *AppError {
	msg, ok := errorMessages[code]
	if !ok {
		msg = "未知错误"
	}
	return &AppError{Code: code, Message: msg, HTTPStatus: httpStatus}
}

func NewWithMessage(code, message string, httpStatus int) *AppError {
	return &AppError{Code: code, Message: message, HTTPStatus: httpStatus}
}

func Message(code string) string {
	if msg, ok := errorMessages[code]; ok {
		return msg
	}
	return "未知错误"
}
