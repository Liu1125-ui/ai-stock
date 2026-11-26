@echo off
setlocal
cd /d "%~dp0"
where pwsh.exe >nul 2>nul
if not errorlevel 1 (
    pwsh.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-system.ps1"
) else if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" (
    "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-system.ps1"
) else (
    echo PowerShell 7 is required. Install it and retry.
    pause
    exit /b 1
)
if errorlevel 1 (
    echo Startup failed. Please keep this window for diagnosis.
    pause
    exit /b 1
)
endlocal
