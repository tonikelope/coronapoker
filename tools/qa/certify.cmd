@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-certification.ps1" %*
exit /b %ERRORLEVEL%
