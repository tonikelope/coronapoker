@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-headless-sim.ps1" %*
exit /b %ERRORLEVEL%
