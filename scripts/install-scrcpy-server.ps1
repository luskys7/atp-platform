# Install scrcpy-server into executor/data/scrcpy/
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$DestDir = Join-Path $Root "executor\data\scrcpy"
$Version = if ($env:ATP_SCRCPY_SERVER_VERSION) { $env:ATP_SCRCPY_SERVER_VERSION } else { "2.4" }
$Dest = Join-Path $DestDir "scrcpy-server"

New-Item -ItemType Directory -Force -Path $DestDir | Out-Null

$Scrcpy = Get-Command scrcpy -ErrorAction SilentlyContinue
if ($Scrcpy) {
    $Bundled = Join-Path (Split-Path $Scrcpy.Source) "scrcpy-server"
    if (Test-Path $Bundled) {
        Copy-Item $Bundled $Dest -Force
        Write-Host "[OK] copied from scrcpy install: $Dest"
        exit 0
    }
}

$Url = "https://github.com/Genymobile/scrcpy/releases/download/v$Version/scrcpy-server-v$Version"
$Tmp = Join-Path $DestDir "scrcpy-server-v$Version"
Write-Host "Downloading $Url"
Invoke-WebRequest -Uri $Url -OutFile $Tmp -UseBasicParsing
Copy-Item $Tmp $Dest -Force
Write-Host "[OK] scrcpy-server v$Version -> $Dest"
Write-Host "Restart Python executor to enable scrcpy mode."
