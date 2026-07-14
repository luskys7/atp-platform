$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

Write-Host "========================================"
Write-Host "  TestFlow - Local Start"
Write-Host "========================================"
Write-Host "ROOT=$Root"
Write-Host ""

# Java
$JavaExe = $null
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $JavaExe = "$env:JAVA_HOME\bin\java.exe"
}
if (-not $JavaExe) {
    $candidates = Get-ChildItem "C:\Program Files\Eclipse Adoptium\jdk-*\bin\java.exe" -ErrorAction SilentlyContinue
    if ($candidates) { $JavaExe = $candidates[0].FullName }
}
if (-not $JavaExe) {
    $JavaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
}
if (-not $JavaExe) { Write-Host "[ERROR] Java not found"; exit 1 }
Write-Host "[OK] Java: $JavaExe"

# Python
$PythonExe = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $PythonExe) { Write-Host "[ERROR] Python not found"; exit 1 }
Write-Host "[OK] Python: $PythonExe"

# Maven
$Mvn = Join-Path $Root "tools\apache-maven-3.9.6\bin\mvn.cmd"
$Jar = Join-Path $Root "backend-java\target\atp-platform-1.0.0.jar"
if (-not (Test-Path $Mvn)) { $Mvn = "mvn"; Write-Host "[WARN] Use system mvn" }
else { Write-Host "[OK] Maven: $Mvn" }

Write-Host ""

if (-not (Test-Path $Jar)) {
    Write-Host "[1/4] Building backend..."
    Push-Location (Join-Path $Root "backend-java")
    & $Mvn -DskipTests package
    if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] Build failed"; exit 1 }
    Pop-Location
    Write-Host "Build done."
} else {
    Write-Host "[1/4] Skip build, jar exists."
}

Write-Host "[2/4] Starting backend..."
Start-Process cmd -ArgumentList "/k", "cd /d `"$(Join-Path $Root 'backend-java')`" & `"$JavaExe`" -jar target\atp-platform-1.0.0.jar --spring.profiles.active=local"

Write-Host "[3/4] Starting executor..."
Start-Process cmd -ArgumentList "/k", "cd /d `"$(Join-Path $Root 'executor')`" & `"$PythonExe`" -m pip install -q fastapi `"uvicorn[standard]`" websockets pydantic & `"$PythonExe`" main.py"

Start-Sleep -Seconds 3

Write-Host "[4/4] Starting frontend..."
Start-Process cmd -ArgumentList "/k", "cd /d `"$(Join-Path $Root 'frontend')`" & npm run dev"

Write-Host ""
Write-Host "========================================"
Write-Host "Frontend: http://localhost:3000"
Write-Host "Backend:  http://localhost:8080/api/health"
Write-Host "Login:    admin / admin123"
Write-Host "========================================"
