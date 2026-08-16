# 从当前 H2 库导出便携种子（控件 + 用例 + 公共步骤 + 套件等）
# 输出：
#   fixtures/portable-seed/*.json
#   fixtures/portable-seed/atp_portable_seed.zip
#   backend-java/src/main/resources/seed/atp_portable_seed.zip  （空库自动导入）
#   backend-java/data/backups/atp_portable_seed.zip          （平台「灾备备份」可还原）
#   backend-java/data/seed/atp_portable_seed.zip
#
# 用法（先停掉占用 DB 的后端，或确保 AUTO_SERVER 可连）:
#   powershell -ExecutionPolicy Bypass -File .\scripts\export-portable-seed.ps1

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$h2 = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\h2database\h2" -Recurse -Filter "h2-*.jar" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$jackson = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\fasterxml\jackson\core\jackson-databind" -Recurse -Filter "jackson-databind-*.jar" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$jacksonCore = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\fasterxml\jackson\core\jackson-core" -Recurse -Filter "jackson-core-*.jar" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$jacksonAnn = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\fasterxml\jackson\core\jackson-annotations" -Recurse -Filter "jackson-annotations-*.jar" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$jsr310 = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\fasterxml\jackson\datatype\jackson-datatype-jsr310" -Recurse -Filter "jackson-datatype-jsr310-*.jar" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName

if (-not $h2 -or -not $jackson) { throw "缺少 h2/jackson 依赖，请先 mvn 编译 backend-java" }

$outDir = Join-Path $root "fixtures\portable-seed"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$buildDir = Join-Path $root "scripts\_seed_build"
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null

$cp = (@($h2, $jackson, $jacksonCore, $jacksonAnn, $jsr310, $buildDir) | Where-Object { $_ }) -join ";"
Write-Host "Compiling SeedJdbcExport..."
javac -encoding UTF-8 -cp $cp -d $buildDir (Join-Path $root "scripts\SeedJdbcExport.java")

$db = (Join-Path $root "backend-java\data\atp_local") -replace '\\', '/'
$url = "jdbc:h2:file:${db};IFEXISTS=TRUE;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE"

Write-Host "Exporting from $db ..."
Remove-Item (Join-Path $root "backend-java\data\atp_local.lock.db") -Force -ErrorAction SilentlyContinue
java -cp $cp SeedJdbcExport $url $outDir

$zip = Join-Path $outDir "atp_portable_seed.zip"
if (-not (Test-Path $zip)) { throw "未生成 zip: $zip" }

# Copy to auto-import / backup locations
$resSeed = Join-Path $root "backend-java\src\main\resources\seed"
$dataSeed = Join-Path $root "backend-java\data\seed"
$backupDir = Join-Path $root "backend-java\data\backups"
foreach ($dir in @($resSeed, $dataSeed, $backupDir)) {
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  Copy-Item $zip (Join-Path $dir "atp_portable_seed.zip") -Force
}
Copy-Item (Join-Path $outDir "manifest.json") (Join-Path $resSeed "manifest.json") -Force

Write-Host ""
Write-Host "Done."
Write-Host "  fixtures:  $outDir"
Write-Host "  classpath: $resSeed\atp_portable_seed.zip"
Write-Host "  backups:   $backupDir\atp_portable_seed.zip"
Write-Host "Next: commit fixtures/portable-seed and resources/seed, or copy zip to peer data/seed then start backend."
