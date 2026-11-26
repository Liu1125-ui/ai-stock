@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0configure-maas.ps1"
if errorlevel 1 echo 配置未完成，请查看上方提示；不要把Key发到聊天中。
pause
