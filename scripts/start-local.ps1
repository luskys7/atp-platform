$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

Write-Host "========================================"
Write-Host "  TestFlow - Local Start"
Write-Host "========================================"
Write-Host "ROOT=$Root"
Write-Host ""

# --- Java ---
$JavaHome = $null
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $JavaHome = $env:JAVA_HOME
}
if (-not $JavaHome) {
    foreach ($candidate in @(
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
    )) {
        if (Test-Path "$candidate\bin\java.exe") { $JavaHome = $candidate; break }
    }
}
if (-not $JavaHome) {
    $adoptium = Get-ChildItem "C:\Program Files\Eclipse Adoptium\jdk-*\bin\java.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($adoptium) { $JavaHome = Split-Path (Split-Path $adoptium.FullName -Parent) -Parent }
}
if (-not $JavaHome) {
    $JavaHome = "C:\Program Files\Java\jdk-21"
    if (-not (Test-Path "$JavaHome\bin\java.exe")) { $JavaHome = $null }
}
if (-not $JavaHome) { Write-Host "[ERROR] Java JDK 17+ not found"; exit 1 }
$env:JAVA_HOME = $JavaHome
$JavaExe = "$JavaHome\bin\java.exe"
Write-Host "[OK] Java: $JavaExe"

# --- Node.js ---
$NodeDir = $null
foreach ($candidate in @(
    "C:\Program Files\nodejs",
    "${env:ProgramFiles(x86)}\nodejs"
)) {
    if (Test-Path "$candidate\npm.cmd") { $NodeDir = $candidate; break }
}
if ($NodeDir) {
    $env:Path = "$NodeDir;" + $env:Path
}
$NpmCmd = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
if (-not $NpmCmd) { Write-Host "[ERROR] Node.js/npm not found (need Node 18+)"; exit 1 }
Write-Host "[OK] Node: $((& node -v) 2>$null)  npm=$NpmCmd"

# --- Python ---
$PythonExe = (Get-Command python -ErrorAction SilentlyContinue).Source
if ($PythonExe) { Write-Host "[OK] Python: $PythonExe" }
else { Write-Host "[WARN] Python not found, skip executor" }

# --- Maven ---
$Mvn = Join-Path $Root "tools\apache-maven-3.9.6\bin\mvn.cmd"
if (-not (Test-Path $Mvn)) {
    $Mvn = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
    if (-not $Mvn) { Write-Host "[ERROR] Maven not found (expected tools\apache-maven-3.9.6)"; exit 1 }
    Write-Host "[WARN] Use system Maven: $Mvn"
} else {
    Write-Host "[OK] Maven: $Mvn"
}

Write-Host ""

# --- Free ports + clear H2 locks (avoid stale loading / DB lock) ---
Write-Host "[prep] Freeing ports 3000/8080/9002 if occupied..."
$ports = 3000, 8080, 9002
Get-NetTCPConnection -LocalPort $ports -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq "Listen" } |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object {
        $p = Get-Process -Id $_ -ErrorAction SilentlyContinue
        if ($p) {
            Write-Host "  stop PID=$_ ($($p.ProcessName))"
            Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
        }
    }
Start-Sleep -Seconds 1
@(
    (Join-Path $Root "backend-java\data\atp_local.lock.db"),
    (Join-Path $Root "data\atp_local.lock.db"),
    (Join-Path (Split-Path $Root -Parent) "data\atp_local.lock.db")
) | ForEach-Object {
    if (Test-Path $_) {
        Remove-Item $_ -Force -ErrorAction SilentlyContinue
        Write-Host "  removed lock: $_"
    }
}

# --- Frontend deps ---
$FrontendDir = Join-Path $Root "frontend"
if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
    Write-Host "[0/3] Installing frontend dependencies..."
    Push-Location $FrontendDir
    & npm.cmd install
    if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Host "[ERROR] npm install failed"; exit 1 }
    Pop-Location
    Write-Host "npm install done."
} else {
    Write-Host "[0/3] Frontend node_modules OK."
}

function Wait-HttpOk([string]$Url, [int]$MaxSeconds = 90, [string]$Label = "service") {
    $deadline = (Get-Date).AddSeconds($MaxSeconds)
    $n = 0
    while ((Get-Date) -lt $deadline) {
        $n++
        try {
            $r = Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) {
                Write-Host "[OK] $Label ready: $Url"
                return $true
            }
        } catch { }
        if ($n % 2 -eq 0) {
            Write-Host "  ... waiting $Label ($([Math]::Min($n * 2, $MaxSeconds))s)"
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "[WARN] $Label not ready within ${MaxSeconds}s: $Url"
    return $false
}

$BackendDir = Join-Path $Root "backend-java"

Write-Host "[1/3] Starting backend (mvn spring-boot:run, :8080)..."
$backendCmd = "set `"JAVA_HOME=$JavaHome`" && cd /d `"$BackendDir`" && `"$Mvn`" -DskipTests spring-boot:run `"-Dspring-boot.run.profiles=local`""
Start-Process cmd -ArgumentList "/k", $backendCmd -WindowStyle Normal
$backendOk = Wait-HttpOk "http://127.0.0.1:8080/api/v1/health" 90 "Backend"

if ($PythonExe) {
    Write-Host "[2/3] Starting executor (:9002)..."
    $executorDir = Join-Path $Root "executor"
    $execCmd = "cd /d `"$executorDir`" && `"$PythonExe`" -m pip install -q fastapi `"uvicorn[standard]`" websockets pydantic requests && `"$PythonExe`" main.py"
    Start-Process cmd -ArgumentList "/k", $execCmd -WindowStyle Normal
    $null = Wait-HttpOk "http://127.0.0.1:9002/health" 30 "Executor"
} else {
    Write-Host "[2/3] Skip executor (no Python)."
}

Write-Host "[3/3] Starting frontend (:3000)..."
$feCmd = "set `"PATH=$NodeDir;%PATH%`" && cd /d `"$FrontendDir`" && npm.cmd run dev"
Start-Process cmd -ArgumentList "/k", $feCmd -WindowStyle Normal
$frontOk = Wait-HttpOk "http://127.0.0.1:3000/" 45 "Frontend"

Write-Host ""
Write-Host "========================================"
Write-Host "Frontend: http://localhost:3000"
Write-Host "Backend:  http://localhost:8080/api/v1/health"
Write-Host "Executor: http://localhost:9002/health"
Write-Host "Login:    admin / admin123"
Write-Host "========================================"
if ($backendOk -and $frontOk) {
    Write-Host "All ready. Open http://localhost:3000"
} elseif (-not $backendOk) {
    Write-Host "Backend not ready. Check the backend CMD window (need 'Started AtpPlatformApplication')."
    Write-Host "Then refresh the browser. Do NOT open the page while backend is still starting."
} else {
    Write-Host "Frontend still starting. Wait a few seconds and open http://localhost:3000"
}
