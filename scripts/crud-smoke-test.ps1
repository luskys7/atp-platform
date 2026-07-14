# TestFlow CRUD smoke test
$ErrorActionPreference = "Stop"
$Base = "http://localhost:8080/api/v1"
$passed = 0
$failed = 0
$skipped = 0
$results = @()

function Test-Crud($module, $name, [scriptblock]$Action) {
    try {
        & $Action
        $script:passed++
        $results += [pscustomobject]@{ Module = $module; Op = $name; Result = "PASS" }
        Write-Host "  PASS $module / $name" -ForegroundColor Green
    } catch {
        $script:failed++
        $results += [pscustomobject]@{ Module = $module; Op = $name; Result = "FAIL"; Detail = $_.Exception.Message }
        Write-Host "  FAIL $module / $name : $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Skip-Crud($module, $name, $reason) {
    $script:skipped++
    $results += [pscustomobject]@{ Module = $module; Op = $name; Result = "SKIP"; Detail = $reason }
    Write-Host "  SKIP $module / $name ($reason)" -ForegroundColor Yellow
}

Write-Host "TestFlow CRUD Smoke Test" -ForegroundColor Cyan
Write-Host "========================"

$login = Invoke-RestMethod -Method Post -Uri "$Base/auth/login" -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$h = @{ Authorization = "Bearer $($login.data.token)" }

Test-Crud "Auth" "update profile" {
    Invoke-RestMethod -Method Put -Uri "$Base/auth/profile" -Headers $h -ContentType "application/json" -Body '{"display_name":"超级管理员"}' | Out-Null
}

Test-Crud "Cases" "folder CRUD" {
    $f = Invoke-RestMethod -Method Post -Uri "$Base/cases/folders" -Headers $h -ContentType "application/json" -Body '{"name":"CRUD-Folder","parent_id":null}'
    $fid = $f.data.id
    $c = Invoke-RestMethod -Method Post -Uri "$Base/cases" -Headers $h -ContentType "application/json" -Body "{`"name`":`"CRUD-Case`",`"platform`":`"android`",`"script_type`":`"python`",`"steps_content`":`"print(1)`",`"folder_id`":$fid,`"case_status`":`"draft`"}"
    $cid = $c.data.id
    Invoke-RestMethod -Method Put -Uri "$Base/cases/$cid" -Headers $h -ContentType "application/json" -Body "{`"name`":`"CRUD-Case-Updated`",`"platform`":`"android`",`"script_type`":`"python`",`"steps_content`":`"print(2)`",`"folder_id`":$fid}" | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$Base/cases/$cid" -Headers $h | Out-Null
}

Test-Crud "Suites" "CRUD" {
    $caseList = Invoke-RestMethod -Uri "$Base/cases?page=1&page_size=1" -Headers $h
    $caseId = if ($caseList.data.list.Count) { $caseList.data.list[0].id } else { 1 }
    $s = Invoke-RestMethod -Method Post -Uri "$Base/suites" -Headers $h -ContentType "application/json" -Body "{`"name`":`"CRUD-Suite`",`"exec_mode`":`"serial`",`"fail_policy`":`"continue_on_fail`",`"items`":[{`"case_id`":$caseId,`"sort_order`":0,`"enabled`":true}]}"
    $sid = $s.data.id
    Invoke-RestMethod -Method Put -Uri "$Base/suites/$sid" -Headers $h -ContentType "application/json" -Body "{`"name`":`"CRUD-Suite-Updated`",`"exec_mode`":`"serial`",`"fail_policy`":`"continue_on_fail`",`"items`":[{`"case_id`":$caseId,`"sort_order`":0,`"enabled`":true}]}" | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$Base/suites/$sid" -Headers $h | Out-Null
}

Test-Crud "Environments" "CRUD" {
    $e = Invoke-RestMethod -Method Post -Uri "$Base/environments" -Headers $h -ContentType "application/json" -Body '{"name":"CRUD-Env","env_type":"test","base_url":"https://test.local","config_json":"{}"}'
    $eid = $e.data.id
    Invoke-RestMethod -Method Put -Uri "$Base/environments/$eid" -Headers $h -ContentType "application/json" -Body '{"name":"CRUD-Env-Updated","env_type":"test","base_url":"https://test.local","config_json":"{}"}' | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$Base/environments/$eid" -Headers $h | Out-Null
}

Test-Crud "Datasets" "CRUD" {
    $d = Invoke-RestMethod -Method Post -Uri "$Base/datasets" -Headers $h -ContentType "application/json" -Body '{"name":"CRUD-DS","description":"t","rows":[{"username":"u1"}]}'
    $did = $d.data.id
    Invoke-RestMethod -Method Put -Uri "$Base/datasets/$did" -Headers $h -ContentType "application/json" -Body '{"name":"CRUD-DS-Updated","description":"t2","rows":[{"username":"u2"}]}' | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$Base/datasets/$did" -Headers $h | Out-Null
}

Test-Crud "CommonSteps" "CRUD" {
    $s = Invoke-RestMethod -Method Post -Uri "$Base/common-steps" -Headers $h -ContentType "application/json" -Body '{"name":"CRUD-Step","description":"d","steps_content":"{\"steps\":[]}"}'
    $sid = $s.data.id
    Invoke-RestMethod -Method Put -Uri "$Base/common-steps/$sid" -Headers $h -ContentType "application/json" -Body '{"name":"CRUD-Step-Updated","description":"d2","steps_content":"{\"steps\":[]}"}' | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$Base/common-steps/$sid" -Headers $h | Out-Null
}

Test-Crud "Schedules" "CRUD" {
    $suiteList = Invoke-RestMethod -Uri "$Base/suites" -Headers $h
    $suiteId = if ($suiteList.data.Count) { $suiteList.data[0].id } else { 1 }
    $sch = Invoke-RestMethod -Method Post -Uri "$Base/schedules" -Headers $h -ContentType "application/json" -Body "{`"name`":`"CRUD-Sch`",`"suite_id`":$suiteId,`"cron_expression`":`"0 0 3 * * ?`",`"enabled`":true}"
    $schId = $sch.data.id
    Invoke-RestMethod -Method Put -Uri "$Base/schedules/$schId" -Headers $h -ContentType "application/json" -Body "{`"name`":`"CRUD-Sch-Updated`",`"suite_id`":$suiteId,`"cron_expression`":`"0 0 4 * * ?`",`"enabled`":true}" | Out-Null
    Invoke-RestMethod -Method Post -Uri "$Base/schedules/$schId/toggle" -Headers $h -ContentType "application/json" -Body '{"enabled":false}' | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$Base/schedules/$schId" -Headers $h | Out-Null
}

Test-Crud "Tasks" "CRUD" {
    $t = Invoke-RestMethod -Method Post -Uri "$Base/tasks" -Headers $h -ContentType "application/json" -Body '{"name":"CRUD-Task","platform":"android","script_type":"python","script_content":"print(1)","parallel_count":1,"timeout_seconds":120,"enable_recording":false}'
    $tid = $t.data.id
    Invoke-RestMethod -Method Put -Uri "$Base/tasks/$tid" -Headers $h -ContentType "application/json" -Body '{"name":"CRUD-Task-Updated","platform":"android","script_type":"python","script_content":"print(2)","parallel_count":1,"timeout_seconds":120,"enable_recording":false}' | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$Base/tasks/$tid" -Headers $h | Out-Null
}

Test-Crud "Controls" "pool create" {
    Invoke-RestMethod -Method Post -Uri "$Base/controls/pool" -Headers $h -ContentType "application/json" -Body '{"element_name":"crud_btn","locator_type":"id","locator_value":"btn_crud","platform":"android","app_package":"com.test"}' | Out-Null
}

Test-Crud "CI" "update config" {
    Invoke-RestMethod -Method Put -Uri "$Base/ci/config" -Headers $h -ContentType "application/json" -Body '{"enabled":false,"jenkins_url":"","job_name":"","token":"","default_timeout_seconds":3600,"default_parallel_count":1}' | Out-Null
}

Test-Crud "Devices" "whitelist + status" {
    $wl = Invoke-RestMethod -Method Post -Uri "$Base/devices/whitelist" -Headers $h -ContentType "application/json" -Body '{"serial_number":"crud-device-001","platform":"android","remark":"crud"}'
    $wlId = $wl.data.id
    Invoke-RestMethod -Method Post -Uri "$Base/agent/devices/register" -ContentType "application/json" -Body '{"serial_number":"crud-device-001","platform":"android","name":"CRUD"}' | Out-Null
    $devList = Invoke-RestMethod -Uri "$Base/devices?page=1&page_size=50" -Headers $h
    $dev = $devList.data.list | Where-Object { $_.serial_number -eq "crud-device-001" } | Select-Object -First 1
    if ($dev) {
        Invoke-RestMethod -Method Put -Uri "$Base/devices/$($dev.id)/status" -Headers $h -ContentType "application/json" -Body '{"status":"maintenance"}' | Out-Null
        Invoke-RestMethod -Method Delete -Uri "$Base/devices/$($dev.id)" -Headers $h | Out-Null
    }
    Invoke-RestMethod -Method Delete -Uri "$Base/devices/whitelist/$wlId" -Headers $h | Out-Null
}

Skip-Crud "AppPackages" "upload/delete" "requires multipart file upload"

Test-Crud "RecycleBin" "list" {
    Invoke-RestMethod -Uri "$Base/recycle-bin" -Headers $h | Out-Null
}

Write-Host ""
Write-Host "Summary: PASS=$passed FAIL=$failed SKIP=$skipped" -ForegroundColor Cyan
$results | Format-Table -AutoSize
if ($failed -gt 0) { exit 1 }
