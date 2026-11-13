@echo off
title Stopp Nimay OMP
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\stop-bridge.ps1" -InstallDir "%~dp0"
timeout /t 3 >nul
