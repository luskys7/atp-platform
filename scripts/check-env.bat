@echo off
setlocal EnableDelayedExpansion
set "PATH=%SystemRoot%\System32;%SystemRoot%;%PATH%"

echo ========================================
echo   TestFlow - Environment Check
echo ========================================
echo.

set ERR=0

echo [1] Java
set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
        if exist "%%D\bin\java.exe" set "JAVA_EXE=%%D\bin\java.exe"
    )
)
if not defined JAVA_EXE (
    echo   [X] Not found - need JDK 17+
    set ERR=1
) else (
    echo   [OK] !JAVA_EXE!
    "!JAVA_EXE!" -version 2>&1 | findstr "version"
)

echo.
echo [2] Maven
if exist "%~dp0..\tools\apache-maven-3.9.6\bin\mvn.cmd" (
    echo   [OK] Built-in Maven found
) else (
    where mvn >nul 2>nul
    if !errorlevel! neq 0 (
        echo   [!] Not found - will download on first build
    ) else (
        echo   [OK] System Maven found
    )
)

echo.
echo [3] Node.js
where node >nul 2>nul
if !errorlevel! neq 0 (echo   [X] Not found & set ERR=1) else (node -v & echo   [OK])

echo.
echo [4] Python
where python >nul 2>nul
if !errorlevel! neq 0 (echo   [X] Not found & set ERR=1) else (python --version & echo   [OK])

echo.
echo [5] Docker (optional)
where docker >nul 2>nul
if !errorlevel! neq 0 (echo   [-] Not installed, use local mode) else (docker --version & echo   [OK])

echo.
echo ========================================
if !ERR! equ 0 (
    echo Ready. Run: scripts\start-local.bat
) else (
    echo Fix [X] items above first.
)
echo ========================================
pause
