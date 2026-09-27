@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-gdx-scenarios.ps1" %*
exit /b %ERRORLEVEL%
