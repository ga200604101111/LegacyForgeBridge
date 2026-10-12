
@echo off
setlocal
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install_LFB.ps1"
set "LFB_EXIT=%ERRORLEVEL%"
echo.
pause
exit /b %LFB_EXIT%
