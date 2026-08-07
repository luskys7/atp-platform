# 有源码时启动远程访客执行器（不打包）
# 用法：
#   .\scripts\start-remote-executor.ps1 -PlatformUrl http://10.0.0.5:8080 -PublicUrl http://10.0.0.12:9002

param(
  [Parameter(Mandatory = $true)][string]$PlatformUrl,
  [Parameter(Mandatory = $true)][string]$PublicUrl,
  [int]$Port = 9002
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Executor = Join-Path $Root "executor"
$Python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $Python) { throw "未找到 python" }

$env:PLATFORM_URL = $PlatformUrl.TrimEnd("/")
$env:ATP_BACKEND_URL = $env:PLATFORM_URL
$env:EXECUTOR_PUBLIC_URL = $PublicUrl.TrimEnd("/")
$env:EXECUTOR_PORT = "$Port"

Write-Host "PLATFORM_URL=$env:PLATFORM_URL"
Write-Host "EXECUTOR_PUBLIC_URL=$env:EXECUTOR_PUBLIC_URL"
Write-Host "EXECUTOR_PORT=$env:EXECUTOR_PORT"
Write-Host "Starting executor..."
Set-Location $Executor
& $Python main.py
