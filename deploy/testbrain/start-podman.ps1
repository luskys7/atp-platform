# Start TestBrain + Milvus with Podman (ASCII-only for Windows PowerShell encoding)
$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

function Resolve-Podman {
    if (Get-Command podman -ErrorAction SilentlyContinue) {
        return (Get-Command podman).Source
    }
    $fallback = "C:\Program Files\RedHat\Podman\podman.exe"
    if (Test-Path $fallback) { return $fallback }
    return $null
}

function Resolve-PodmanCompose {
    if (Get-Command podman-compose -ErrorAction SilentlyContinue) {
        return (Get-Command podman-compose).Source
    }
    foreach ($c in @(
        "$env:APPDATA\Python\Python311\Scripts\podman-compose.exe",
        "$env:APPDATA\Python\Python312\Scripts\podman-compose.exe"
    )) {
        if (Test-Path $c) { return $c }
    }
    return $null
}

$podman = Resolve-Podman
if (-not $podman) {
    Write-Host "ERROR: podman.exe not found. Install/start Podman Desktop."
    exit 1
}
$podmanDir = Split-Path $podman -Parent
$env:Path = "$podmanDir;$env:Path"

$compose = Resolve-PodmanCompose
if (-not $compose) {
    Write-Host "ERROR: podman-compose not found. Run: pip install podman-compose"
    exit 1
}
$env:Path = "$(Split-Path $compose -Parent);$env:Path"

if (-not (Test-Path ".env")) {
    Write-Host "ERROR: missing .env"
    exit 1
}
if (-not (Test-Path "vendor\TestBrain\manage.py")) {
    Write-Host "ERROR: missing vendor\TestBrain"
    exit 1
}

Write-Host "podman=$podman"
Write-Host "compose=$compose"

Write-Host "=== build testbrain ==="
& $compose -f docker-compose.yml build testbrain
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: build failed"
    exit $LASTEXITCODE
}

$order = @("etcd", "minio", "testbrain-mysql", "milvus", "testbrain")
foreach ($svc in $order) {
    Write-Host "=== up $svc ==="
    & $compose -f docker-compose.yml up -d --no-deps $svc
    if ($LASTEXITCODE -ne 0) {
        Write-Host "WARN: compose up $svc failed, try podman run fallback for etcd/minio if needed"
        if ($svc -eq "etcd") {
            & $podman rm -f tb-etcd 2>$null
            & $podman volume create atp-testbrain_tb_etcd 2>$null
            & $podman run -d --name tb-etcd --replace `
                --network atp-testbrain_default `
                -e ETCD_AUTO_COMPACTION_MODE=revision `
                -e ETCD_AUTO_COMPACTION_RETENTION=1000 `
                -e ETCD_QUOTA_BACKEND_BYTES=4294967296 `
                -e ETCD_SNAPSHOT_COUNT=50000 `
                -v atp-testbrain_tb_etcd:/etcd `
                docker.m.daocloud.io/quay.io/coreos/etcd:v3.5.16 `
                etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
        }
    }
    Start-Sleep -Seconds 5
}

Write-Host "=== ps ==="
& $compose -f docker-compose.yml ps
& $podman ps -a --filter "name=tb-"
Write-Host "Check: curl http://127.0.0.1:8000/api/v1/health"
Write-Host "Check: curl http://127.0.0.1:9091/healthz"
