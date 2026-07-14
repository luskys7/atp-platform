@echo off
setlocal EnableDelayedExpansion

set "PATH=%SystemRoot%\System32;%SystemRoot%;%PATH%"

pushd "%~dp0.."
set "ROOT=%CD%"
popd

set "MVN=%ROOT%\tools\apache-maven-3.9.6\bin\mvn.cmd"
set "JAR=%ROOT%\backend-java\target\atp-platform-1.0.0.jar"

echo ========================================
echo   TestFlow - Local Start
echo ========================================
echo.
echo ROOT=%ROOT%
echo.

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe" set "JAVA_EXE=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe"
if not defined JAVA_EXE (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
        if not defined JAVA_EXE if exist "%%D\bin\java.exe" set "JAVA_EXE=%%D\bin\java.exe"
    )
)
if not defined JAVA_EXE (
    for /f "delims=" %%J in ('where java 2^>nul') do (
        if not defined JAVA_EXE set "JAVA_EXE=%%J"
    )
)
if not defined JAVA_EXE (
    echo [ERROR] Java not found
    pause
    exit /b 1
)
echo [OK] Java: !JAVA_EXE!

set "PYTHON_EXE="
for /f "delims=" %%P in ('where python 2^>nul') do (
    if not defined PYTHON_EXE set "PYTHON_EXE=%%P"
)
if not defined PYTHON_EXE (
    echo [ERROR] Python not found
    pause
    exit /b 1
)
echo [OK] Python: !PYTHON_EXE!

if not exist "%MVN%" (
    set "MVN=mvn"
    echo [WARN] Use system mvn
) else (
    echo [OK] Maven: %MVN%
)

echo.

echo [1/4] Building backend...
pushd "%ROOT%\backend-java"
call "%MVN%" -DskipTests package
if errorlevel 1 (
    echo [ERROR] Build failed
    popd
    pause
    exit /b 1
)
popd
echo Build done.

echo [2/4] Starting backend...
start "TestFlow-Backend" cmd /k "cd /d "%ROOT%\backend-java" & "%JAVA_EXE%" -jar target\atp-platform-1.0.0.jar --spring.profiles.active=local"

echo [3/4] Starting executor...
start "TestFlow-Executor" cmd /k "cd /d "%ROOT%\executor" & "%PYTHON_EXE%" -m pip install -q fastapi uvicorn[standard] websockets pydantic & "%PYTHON_EXE%" main.py"

ping -n 4 127.0.0.1 >nul

echo [4/4] Starting frontend...
start "TestFlow-Frontend" cmd /k "cd /d "%ROOT%\frontend" & npm run dev"

echo.
echo ========================================
echo Frontend: http://localhost:3000
echo Backend:  http://localhost:8080/api/health
echo Login:    admin / admin123
echo ========================================
pause
