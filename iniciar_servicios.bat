@echo off
setlocal
if /i "%~1"=="identidad" goto identidad
call "%~dp0iniciar_checkpoint_local.bat"
exit /b %errorlevel%

:identidad
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar_identidad.ps1"
set "resultado=%errorlevel%"
pause
exit /b %resultado%
