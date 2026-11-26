@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0init-admin.ps1"
set "taskExit=%ERRORLEVEL%"
echo.
if not "%taskExit%"=="0" echo Admin initialization failed. Review the status above.
pause
exit /b %taskExit%
