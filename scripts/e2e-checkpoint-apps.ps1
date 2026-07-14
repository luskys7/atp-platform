# TestFlow E2E: App packages + checkpoint resume
$ErrorActionPreference = "Stop"
$Base = "http://localhost:8080/api/v1"
$Root = Split-Path -Parent $PSScriptRoot

function Write-Step($msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }
function Assert-Ok($cond, $msg) {
    if (-not $cond) { throw "ASSERT FAIL: $msg" }
    Write-Host "  OK: $msg" -ForegroundColor Green
}

Write-Host "========================================"
Write-Host "  TestFlow E2E: Checkpoint + AppPackages"
Write-Host "========================================"

Write-Step "0. Health check"
Assert-Ok ((Invoke-RestMethod "$Base/../health" -TimeoutSec 5).status -eq "ok") "backend health"
try {
    Invoke-RestMethod "http://localhost:9002/health" -TimeoutSec 5 | Out-Null
    Write-Host "  OK: executor health" -ForegroundColor Green
} catch {
    Write-Host "  WARN: executor not running" -ForegroundColor Yellow
}

Write-Step "1. Login"
$login = Invoke-RestMethod -Method Post -Uri "$Base/auth/login" -ContentType "application/json" `
    -Body '{"username":"admin","password":"admin123"}' -TimeoutSec 10
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }
Assert-Ok $token "token acquired"

Write-Step "2. Register test device"
$regBody = '{"serial_number":"local-test-device","name":"E2E Device","platform":"android","os_version":"13","model":"E2E","battery_level":100}'
try {
    $dev = Invoke-RestMethod -Method Post -Uri "$Base/agent/devices/register" -ContentType "application/json" `
        -Body $regBody -TimeoutSec 10
    $deviceId = $dev.data.id
} catch {
    $list = Invoke-RestMethod -Uri "$Base/devices?page=1&page_size=5" -Headers $headers -TimeoutSec 10
    $deviceId = ($list.data.list | Where-Object { $_.serial_number -eq "local-test-device" } | Select-Object -First 1).id
}
Assert-Ok $deviceId "device id=$deviceId"
Invoke-RestMethod -Method Post -Uri "$Base/agent/devices/heartbeat" -ContentType "application/json" `
    -Body '{"serial_number":"local-test-device","battery_level":100}' -TimeoutSec 10 | Out-Null

Write-Step "3. Create failing python case"
$ts = Get-Date -Format "HHmmss"
$pyScript = @'
import time

# Step 1: wait
try:
    time.sleep(0.5)
except Exception:
    print('CHECKPOINT_FAILED:step=1')
    raise

# Step 2: deliberate fail
try:
    raise AssertionError('E2E_FAIL_MARKER_XYZ')
except Exception:
    print('CHECKPOINT_FAILED:step=2')
    raise
'@
$escaped = $pyScript -replace '\\','\\\\' -replace '"','\"' -replace "`n",'\n'
$caseBody = "{`"name`":`"E2E-Case-$ts`",`"platform`":`"android`",`"script_type`":`"python`",`"steps_content`":`"$escaped`",`"case_status`":`"active`",`"enable_recording`":false,`"timeout_seconds`":120}"
$case = Invoke-RestMethod -Method Post -Uri "$Base/cases" -Headers $headers -ContentType "application/json" `
    -Body $caseBody -TimeoutSec 15
$caseId = $case.data.id
Assert-Ok $caseId "case id=$caseId"

Write-Step "4. Create suite and run"
$suiteBody = "{`"name`":`"E2E-Suite-$ts`",`"exec_mode`":`"serial`",`"fail_policy`":`"continue_on_fail`",`"items`":[{`"case_id`":$caseId,`"sort_order`":0,`"enabled`":true}]}"
$suite = Invoke-RestMethod -Method Post -Uri "$Base/suites" -Headers $headers -ContentType "application/json" `
    -Body $suiteBody -TimeoutSec 15
$suiteId = $suite.data.id
Assert-Ok $suiteId "suite id=$suiteId"

$run = Invoke-RestMethod -Method Post -Uri "$Base/suites/$suiteId/run" -Headers $headers -TimeoutSec 30
$runId = $run.data.suite_run_id
Assert-Ok $runId "run id=$runId"

Write-Step "5. Wait for batch failure (max 90s)"
$failedStep = $null
$runStatus = $null
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 2
    $detail = Invoke-RestMethod -Uri "$Base/suite-runs/$runId" -Headers $headers -TimeoutSec 10
    $runStatus = $detail.data.run.status
    $item = $detail.data.items | Select-Object -First 1
    if ($item.status -eq "failed") {
        $failedStep = $item.failed_step_index
        break
    }
    if ($runStatus -in @("failed", "completed")) { break }
    Write-Host "  ... run=$runStatus item=$($item.status)" -ForegroundColor DarkGray
}
Assert-Ok ($failedStep -eq 2) "failed_step_index=2 (actual=$failedStep run=$runStatus)"

Write-Step "6. Checkpoint resume"
$resume = Invoke-RestMethod -Method Post -Uri "$Base/suite-runs/$runId/resume" -Headers $headers -TimeoutSec 30
Assert-Ok ($resume.data.resumed_count -ge 1) "resumed_count=$($resume.data.resumed_count)"

Write-Step "7. Upload test APK"
$apkDir = Join-Path $Root "backend-java\data\apps"
New-Item -ItemType Directory -Force -Path $apkDir | Out-Null
$apkPath = Join-Path $env:TEMP "testflow-e2e.apk"
[System.IO.File]::WriteAllBytes($apkPath, [byte[]](0x50, 0x4B, 0x03, 0x04, 0x54, 0x65, 0x73, 0x74))

Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token)
$content = New-Object System.Net.Http.MultipartFormDataContent
$fileStream = [System.IO.File]::OpenRead($apkPath)
$fileContent = New-Object System.Net.Http.StreamContent($fileStream)
$fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/vnd.android.package-archive")
$content.Add($fileContent, "file", "testflow-e2e.apk")
$content.Add((New-Object System.Net.Http.StringContent("E2E-Test.apk")), "name")
$content.Add((New-Object System.Net.Http.StringContent("com.testflow.e2e")), "package_name")
$content.Add((New-Object System.Net.Http.StringContent("1.0.0")), "version_name")
$content.Add((New-Object System.Net.Http.StringContent("android")), "platform")
$response = $client.PostAsync("$Base/app-packages", $content).Result
$uploadJson = $response.Content.ReadAsStringAsync().Result
$fileStream.Close()
$client.Dispose()
$upload = $uploadJson | ConvertFrom-Json
if ($upload.code -ne 0) { throw "Upload failed: $($upload.message)" }
$pkgId = $upload.data.id
Assert-Ok $pkgId "package id=$pkgId"

Write-Step "8. Batch install"
$installBody = "{`"device_ids`":[$deviceId]}"
$install = Invoke-RestMethod -Method Post -Uri "$Base/app-packages/$pkgId/batch-install" -Headers $headers `
    -ContentType "application/json" -Body $installBody -TimeoutSec 180
Assert-Ok ($null -ne $install.data.results) "batch install returned results"
Write-Host "  install: success=$($install.data.success_count) failed=$($install.data.failed_count)" -ForegroundColor Yellow

Write-Step "9. List APIs"
$pkgs = Invoke-RestMethod -Uri "$Base/app-packages" -Headers $headers -TimeoutSec 10
Assert-Ok ($pkgs.data.Count -ge 1) "app packages count=$($pkgs.data.Count)"
$runs = Invoke-RestMethod -Uri "$Base/suites/$suiteId/runs" -Headers $headers -TimeoutSec 10
Assert-Ok ($runs.data.Count -ge 1) "suite runs count=$($runs.data.Count)"

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  E2E PASSED" -ForegroundColor Green
Write-Host "  case=$caseId suite=$suiteId run=$runId package=$pkgId"
Write-Host "========================================"
