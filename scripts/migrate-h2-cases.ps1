# 从另一套 H2 库合并迁移「用例 / 目录 / 公共步骤 / 套件」到当前 atp_local
# 控件池：若源库为空则跳过；不会覆盖当前已有控件。
# 用法（先停掉占用 DB 的 Java 后端）:
#   powershell -ExecutionPolicy Bypass -File .\scripts\migrate-h2-cases.ps1 -SourceDb "E:\自动化测试平台-1\backend-java\data\atp_local"
#
param(
  [Parameter(Mandatory = $true)]
  [string]$SourceDb,
  [string]$TargetDb = "",
  [long]$IdOffset = 10000
)

$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -ErrorAction SilentlyContinue
if (-not $root) { $root = (Resolve-Path "$PSScriptRoot\..").Path }
if (-not $TargetDb) {
  $TargetDb = Join-Path $root "backend-java\data\atp_local"
}

$h2 = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\h2database\h2" -Recurse -Filter "h2-*.jar" |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $h2) { throw "未找到 h2 jar，请先编译过 backend-java（maven 缓存）" }

function ToJdbcFile([string]$path) {
  $p = (Resolve-Path $path).Path -replace '\\', '/'
  if ($p -match '\.mv\.db$') { $p = $p -replace '\.mv\.db$', '' }
  return $p
}

$srcFile = if (Test-Path "$SourceDb.mv.db") { "$SourceDb.mv.db" } elseif (Test-Path $SourceDb) { $SourceDb } else { throw "源库不存在: $SourceDb" }
$srcCopyDir = Join-Path $root "backend-java\data\_migrate_src"
New-Item -ItemType Directory -Force -Path $srcCopyDir | Out-Null
Copy-Item $srcFile (Join-Path $srcCopyDir "atp_local.mv.db") -Force

$bakDir = Join-Path $root "backend-java\data\backups"
New-Item -ItemType Directory -Force -Path $bakDir | Out-Null
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item "$TargetDb.mv.db" (Join-Path $bakDir "pre_migrate_$ts.mv.db") -Force
Remove-Item "$TargetDb.lock.db" -Force -ErrorAction SilentlyContinue

$csvDir = Join-Path $root "backend-java\data\_migrate_inspect\csv"
New-Item -ItemType Directory -Force -Path $csvDir | Out-Null
$exp = ($csvDir -replace '\\', '/')

$srcUrl = "jdbc:h2:file:$(ToJdbcFile (Join-Path $srcCopyDir 'atp_local'));IFEXISTS=TRUE;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
$dstUrl = "jdbc:h2:file:$(ToJdbcFile $TargetDb);IFEXISTS=TRUE;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE"

$exportSql = Join-Path $csvDir "export.sql"
@"
-- Windows 中文环境默认 GBK：不显式 charset，用系统编码写出，导入时用 GBK 读入
CALL CSVWRITE('$exp/folders.csv', 'SELECT id+$IdOffset AS id, created_at, name, CASE WHEN parent_id IS NULL THEN NULL ELSE parent_id+$IdOffset END AS parent_id, sort_order, team_id FROM case_folders');
CALL CSVWRITE('$exp/steps.csv', 'SELECT id+$IdOffset AS id, created_at, created_by, deleted_at, description, input_params, name, output_params, status, steps_content, updated_at FROM common_steps');
CALL CSVWRITE('$exp/cases.csv', 'SELECT id+$IdOffset AS id, app_package, case_status, created_at, CAST(NULL AS BIGINT) AS dataset_id, defect_id, deleted_at, enable_recording, CAST(NULL AS BIGINT) AS env_id, expected_result, CASE WHEN folder_id IS NULL THEN NULL ELSE folder_id+$IdOffset END AS folder_id, CAST(NULL AS VARCHAR) AS module_name, name, owner_id, platform, preconditions, priority, requirement_id, script_type, steps_content, tags, team_id, timeout_seconds, updated_at, version_num FROM test_cases');
CALL CSVWRITE('$exp/suites.csv', 'SELECT id+$IdOffset AS id, created_at, created_by, deleted_at, description, CAST(NULL AS BIGINT) AS env_id, exec_mode, fail_policy, hook_after, hook_before, name, tags, team_id, updated_at FROM test_suites');
CALL CSVWRITE('$exp/suite_items.csv', 'SELECT id+$IdOffset AS id, case_id+$IdOffset AS case_id, enabled, sort_order, suite_id+$IdOffset AS suite_id FROM test_suite_items');
CALL CSVWRITE('$exp/controls.csv', 'SELECT * FROM control_pools');
"@ | Set-Content $exportSql -Encoding ASCII

Write-Host "Exporting from source..."
java -cp $h2 org.h2.tools.RunScript "-url" $srcUrl "-user" "sa" "-script" $exportSql | Out-Host

$importSql = Join-Path $csvDir "import.sql"
@"
INSERT INTO case_folders SELECT * FROM CSVREAD('$exp/folders.csv', null, 'charset=GBK');
INSERT INTO common_steps SELECT * FROM CSVREAD('$exp/steps.csv', null, 'charset=GBK') s WHERE NOT EXISTS (SELECT 1 FROM common_steps c WHERE c.name = s.name);
INSERT INTO test_cases SELECT * FROM CSVREAD('$exp/cases.csv', null, 'charset=GBK');
INSERT INTO test_suites SELECT * FROM CSVREAD('$exp/suites.csv', null, 'charset=GBK');
INSERT INTO test_suite_items SELECT * FROM CSVREAD('$exp/suite_items.csv', null, 'charset=GBK');
-- 控件：仅当目标库控件数为 0 时整表导入（避免覆盖当前控件）
INSERT INTO control_pools
SELECT * FROM CSVREAD('$exp/controls.csv', null, 'charset=GBK') src
WHERE (SELECT COUNT(*) FROM control_pools) = 0;

ALTER TABLE case_folders ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM case_folders);
ALTER TABLE common_steps ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM common_steps);
ALTER TABLE test_cases ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM test_cases);
ALTER TABLE test_suites ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM test_suites);
ALTER TABLE test_suite_items ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM test_suite_items);
ALTER TABLE control_pools ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM control_pools);

SELECT 'control_pools' t, COUNT(*) c FROM control_pools
UNION ALL SELECT 'test_cases', COUNT(*) FROM test_cases
UNION ALL SELECT 'case_folders', COUNT(*) FROM case_folders
UNION ALL SELECT 'common_steps', COUNT(*) FROM common_steps
UNION ALL SELECT 'test_suites', COUNT(*) FROM test_suites;
"@ | Set-Content $importSql -Encoding ASCII

Write-Host "Importing into target (stop backend first if locked)..."
java -cp $h2 org.h2.tools.RunScript "-url" $dstUrl "-user" "sa" "-script" $importSql "-showResults" | Out-Host
Write-Host "Done. Backup: $bakDir\pre_migrate_$ts.mv.db"
