from typing import Optional, List
from pydantic import BaseModel, Field


class DeviceInfo(BaseModel):
    id: int
    serial_number: str
    platform: str
    os_version: str = ""
    model: str = ""
    adb_port: int = 5037
    wda_port: int = 8100
    agent_host: str = ""
    agent_port: int = 0


class ExecuteRequest(BaseModel):
    task_id: int
    execution_id: int
    device: DeviceInfo
    script_type: str = Field(description="python | appium")
    script_content: str
    app_package: str = ""
    app_path: str = ""
    enable_recording: bool = True
    timeout_seconds: int = 3600


class LogEntry(BaseModel):
    log_type: str = "script"
    level: str = "info"
    message: str
    stack_trace: str = ""


class RecordingInfo(BaseModel):
    file_name: str
    file_path: str
    file_size: int = 0
    duration_seconds: int = 0
    watermark_hash: str = ""


class ExecuteResult(BaseModel):
    success: bool
    result_summary: str = ""
    error_code: Optional[str] = None
    error_message: Optional[str] = None
    logs: List[LogEntry] = []
    recording: Optional[RecordingInfo] = None


class CancelRequest(BaseModel):
    execution_id: int
