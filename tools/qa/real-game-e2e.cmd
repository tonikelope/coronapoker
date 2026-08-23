@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-real-game-e2e.ps1" %*
exit /b %ERRORLEVEL%
