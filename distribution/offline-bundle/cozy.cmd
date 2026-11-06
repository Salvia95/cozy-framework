@echo off
rem Cozy command for Windows: cozy <action> [args...]   Run "cozy help" for details.
rem In the bundle folder cozy.ps1 sits next to this file; after "cozy install" it is in ..\lib\.
setlocal
set "COZY_PS1=%~dp0cozy.ps1"
if not exist "%COZY_PS1%" set "COZY_PS1=%~dp0..\lib\cozy.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File "%COZY_PS1%" %*
exit /b %ERRORLEVEL%
