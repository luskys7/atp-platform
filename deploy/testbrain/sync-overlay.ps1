# 将本地 overlay 定制 UI 同步到运行中的 TestBrain 容器（默认 tb-app）
# 用法：
#   .\sync-overlay.ps1
#   .\sync-overlay.ps1 -Container remote-tb-app   # 若远程宿主机上容器名不同

param(
  [string]$Container = "tb-app"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$overlay = Join-Path $root "overlay"

if (-not (Test-Path $overlay)) {
  throw "overlay 目录不存在: $overlay"
}

Write-Host "Sync overlay -> container '$Container' ..."

podman cp "$overlay\templates\." "${Container}:/app/templates/"
podman cp "$overlay\apps\core\dashboard_views.py" "${Container}:/app/apps/core/dashboard_views.py"
podman cp "$overlay\apps\core\upload_views.py" "${Container}:/app/apps/core/upload_views.py"
podman cp "$overlay\apps\core\urls.py" "${Container}:/app/apps/core/urls.py"
podman cp "$overlay\apps\core\static\." "${Container}:/app/apps/core/static/"
podman cp "$overlay\apps\ai_agents\test_case_generator\." "${Container}:/app/apps/ai_agents/test_case_generator/"
podman cp "$overlay\apps\ai_agents\test_case_reviewer\." "${Container}:/app/apps/ai_agents/test_case_reviewer/"
podman cp "$overlay\config\embed_middleware.py" "${Container}:/app/config/embed_middleware.py"
podman cp "$overlay\config\settings_docker.py" "${Container}:/app/config/settings_docker.py"

Write-Host "Restarting $Container ..."
podman restart $Container | Out-Null
Write-Host "Done. 强制刷新 ATP 内嵌 TestBrain 页面即可。"
