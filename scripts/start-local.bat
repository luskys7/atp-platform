@echo off
setlocal EnableDelayedExpansion

set "PATH=%SystemRoot%\System32;%SystemRoot%;%PATH%"

pushd "%~dp0.."
set "ROOT=%CD%"
popd

set "MVN=%ROOT%\tools\apache-maven-3.9.6\bin\mvn.cmd"

echo ========================================
echo   TestFlow - Local Start
echo ========================================
echo.
echo ROOT=%ROOT%
echo.

REM --- Java ---
set "JAVA_HOME_RESOLVED="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME_RESOLVED=%JAVA_HOME%"
if not defined JAVA_HOME_RESOLVED if exist "C:\Program Files\Java\jdk-21\bin\java.exe" set "JAVA_HOME_RESOLVED=C:\Program Files\Java\jdk-21"
if not defined JAVA_HOME_RESOLVED if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set "JAVA_HOME_RESOLVED=C:\Program Files\Java\jdk-17"
if not defined JAVA_HOME_RESOLVED (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
        if not defined JAVA_HOME_RESOLVED if exist "%%D\bin\java.exe" set "JAVA_HOME_RESOLVED=%%D"
    )
)
if not defined JAVA_HOME_RESOLVED (
    echo [ERROR] Java JDK 17+ not found
    pause
    exit /b 1
)
set "JAVA_HOME=%JAVA_HOME_RESOLVED%"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
echo [OK] Java: !JAVA_EXE!

REM --- Node.js ---
set "NODE_DIR="
if exist "C:\Program Files\nodejs\npm.cmd" set "NODE_DIR=C:\Program Files\nodejs"
if not defined NODE_DIR if exist "%ProgramFiles(x86)%\nodejs\npm.cmd" set "NODE_DIR=%ProgramFiles(x86)%\nodejs"
if defined NODE_DIR set "PATH=%NODE_DIR%;%PATH%"
where npm.cmd >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Node.js/npm not found ^(need Node 18+^)
    pause
    exit /b 1
)
echo [OK] Node.js / npm

REM --- Python ---
set "PYTHON_EXE="
for /f "delims=" %%P in ('where python 2^>nul') do (
    if not defined PYTHON_EXE set "PYTHON_EXE=%%P"
)
if defined PYTHON_EXE (
    echo [OK] Python: !PYTHON_EXE!
) else (
    echo [WARN] Python not found, skip executor
)

if not exist "%MVN%" (
    where mvn.cmd >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] Maven not found ^(expected tools\apache-maven-3.9.6^)
        pause
        exit /b 1
    )
    set "MVN=mvn"
    echo [WARN] Use system Maven
) else (
    echo [OK] Maven: %MVN%
)

echo.
echo [prep] Free ports 3000/8080/9002 ...
for %%P in (3000 8080 9002) do (
  for /f "tokens=5" %%A in ('netstat -ano ^| findstr ":%%P" ^| findstr "LISTENING"') do (
    echo   stop PID=%%A on %%P
    taskkill /F /PID %%A >nul 2>nul
  )
)
if exist "%ROOT%\backend-java\data\atp_local.lock.db" del /f /q "%ROOT%\backend-java\data\atp_local.lock.db"
if exist "%ROOT%\data\atp_local.lock.db" del /f /q "%ROOT%\data\atp_local.lock.db"

REM --- Frontend deps ---
if not exist "%ROOT%\frontend\node_modules\" (
    echo [0/3] Installing frontend dependencies...
    pushd "%ROOT%\frontend"
    call npm.cmd install
    if errorlevel 1 (
        echo [ERROR] npm install failed
        popd
        pause
        exit /b 1
    )
    popd
    echo npm install done.
) else (
    echo [0/3] Frontend node_modules OK.
)

echo [1/3] Starting backend ^(mvn spring-boot:run, :8080^)...
start "TestFlow-Backend" cmd /k "set JAVA_HOME=%JAVA_HOME%&& cd /d "%ROOT%\backend-java" && "%MVN%" -DskipTests spring-boot:run "-Dspring-boot.run.profiles=local""

echo Waiting for backend health ...
set "BACKEND_OK=0"
for /L %%I in (1,1,45) do (
  powershell -NoProfile -Command "try { $r=Invoke-WebRequest http://127.0.0.1:8080/api/v1/health -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }"
  if not errorlevel 1 (
    set "BACKEND_OK=1"
    echo [OK] Backend ready
    goto backend_done
  )
  ping -n 3 127.0.0.1 >nul
  echo   ... waiting backend %%I/45
)
echo [WARN] Backend not healthy yet. Check TestFlow-Backend window.
:backend_done

if defined PYTHON_EXE (
    echo [2/3] Starting executor ^(:9002^)...
    start "TestFlow-Executor" cmd /k "cd /d "%ROOT%\executor" && "%PYTHON_EXE%" -m pip install -q fastapi uvicorn[standard] websockets pydantic requests && "%PYTHON_EXE%" main.py"
    ping -n 5 127.0.0.1 >nul
) else (
    echo [2/3] Skip executor ^(no Python^).
)

echo [3/3] Starting frontend ^(:3000^)...
start "TestFlow-Frontend" cmd /k "set PATH=%NODE_DIR%;%PATH%&& cd /d "%ROOT%\frontend" && npm.cmd run dev"

echo Waiting for frontend ...
for /L %%I in (1,1,20) do (
  powershell -NoProfile -Command "try { $r=Invoke-WebRequest http://127.0.0.1:3000/ -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }"
  if not errorlevel 1 (
    echo [OK] Frontend ready
    goto fe_done
  )
  ping -n 3 127.0.0.1 >nul
)
echo [WARN] Frontend still starting
:fe_done

echo.
echo ========================================
echo Frontend: http://localhost:3000
echo Backend:  http://localhost:8080/api/v1/health
echo Executor: http://localhost:9002/health
echo Login:    admin / admin123
echo ========================================
if "%BACKEND_OK%"=="1" (
  echo All set. Open http://localhost:3000
) else (
  echo Wait until backend shows Started AtpPlatformApplication, then refresh browser.
)
pause
