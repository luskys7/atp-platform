# Build guest launcher -> backend-java/data/downloads/TestFlow-Executor.exe
# Usage:
#   .\scripts\build-executor-exe.ps1
#   .\scripts\build-executor-exe.ps1 -SkipInstall

param(
  [switch]$SkipInstall,
  [switch]$OneDir
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Executor = Join-Path $Root "executor"
$OutDir = Join-Path $Root "backend-java\data\downloads"
$Work = Join-Path $Root "build\executor-launcher"
$Dist = Join-Path $Work "dist"
$Python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $Python) { throw "python not found (need Python 3.10+)" }

Write-Host "Python: $Python"
Write-Host "Executor: $Executor"

if (-not $SkipInstall) {
  Write-Host "[1/4] Installing build deps..."
  & $Python -m pip install -q --upgrade pip
  & $Python -m pip install -q pyinstaller `
    "fastapi==0.109.0" "uvicorn[standard]==0.27.0" "websockets>=12.0" `
    "pydantic==2.5.3" "requests>=2.31.0" "Pillow>=10.0.0"
}

New-Item -ItemType Directory -Force -Path $Work, $OutDir | Out-Null
if (Test-Path $Dist) { Remove-Item -Recurse -Force $Dist }
$buildDir = Join-Path $Work "build"
if (Test-Path $buildDir) { Remove-Item -Recurse -Force $buildDir }

$hidden = New-Object System.Collections.Generic.List[string]
@(
  "main", "platform_sync", "adb_discovery", "screen_stream", "scrcpy_stream",
  "execution_context", "uvicorn", "uvicorn.logging", "uvicorn.loops",
  "uvicorn.loops.auto", "uvicorn.protocols", "uvicorn.protocols.http",
  "uvicorn.protocols.http.auto", "uvicorn.protocols.websockets",
  "uvicorn.protocols.websockets.auto", "uvicorn.lifespan", "uvicorn.lifespan.on",
  "fastapi", "starlette", "pydantic", "requests", "websockets"
) | ForEach-Object { [void]$hidden.Add($_) }

Get-ChildItem $Executor -Filter "*.py" | ForEach-Object {
  $name = $_.BaseName
  if ($name -and $name -ne "guest_launcher" -and -not $hidden.Contains($name)) {
    [void]$hidden.Add($name)
  }
}

$hideArgs = @()
foreach ($h in $hidden) { $hideArgs += @("--hidden-import", $h) }

$modeArgs = if ($OneDir) { @("--onedir") } else { @("--onefile") }

Write-Host "[2/4] PyInstaller packaging..."
$pyArgs = New-Object System.Collections.Generic.List[string]
if ($OneDir) { [void]$pyArgs.Add("--onedir") } else { [void]$pyArgs.Add("--onefile") }
@(
  "--noconfirm", "--clean", "--windowed",
  "--name", "TestFlow-Executor",
  "--distpath", $Dist,
  "--workpath", $buildDir,
  "--specpath", $Work,
  "-p", $Executor
) | ForEach-Object { [void]$pyArgs.Add($_) }
foreach ($h in $hidden) {
  [void]$pyArgs.Add("--hidden-import")
  [void]$pyArgs.Add($h)
}
[void]$pyArgs.Add((Join-Path $Executor "guest_launcher.py"))

Write-Host ("PyInstaller args count: {0}" -f $pyArgs.Count)
Push-Location $Executor
try {
  & $Python -m PyInstaller @($pyArgs.ToArray())
  if ($LASTEXITCODE -ne 0) { throw "PyInstaller failed: exit=$LASTEXITCODE" }
} finally {
  Pop-Location
}

Write-Host "[3/4] Copy to downloads..."
if ($OneDir) {
  $exeSrc = Join-Path $Dist "TestFlow-Executor\TestFlow-Executor.exe"
} else {
  $exeSrc = Join-Path $Dist "TestFlow-Executor.exe"
}
if (-not (Test-Path $exeSrc)) { throw "artifact missing: $exeSrc" }

$exeDst = Join-Path $OutDir "TestFlow-Executor.exe"
Copy-Item $exeSrc $exeDst -Force

if ($OneDir) {
  $bundleDst = Join-Path $OutDir "TestFlow-Executor-bundle"
  if (Test-Path $bundleDst) { Remove-Item -Recurse -Force $bundleDst }
  Copy-Item (Join-Path $Dist "TestFlow-Executor") $bundleDst -Recurse -Force
}

$len = (Get-Item $exeDst).Length
Write-Host ("[4/4] Done: {0} ({1} MB)" -f $exeDst, [math]::Round($len / 1MB, 2))
Write-Host "Download launcher button should work now."
