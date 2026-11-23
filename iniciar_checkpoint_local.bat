@echo off
setlocal
title CheckPoint Store - Eureka, Identidad, Catalogo, Ventas y Gateway
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar_identidad.ps1" -IncluirCatalogo
set "resultado=%errorlevel%"
pause
exit /b %resultado%
