@echo off
title Nimay OMP Bridge
cd /d "%~dp0bridge"
where node >nul 2>nul
if errorlevel 1 (
  set "NODE=C:\Program Files\nodejs\node.exe"
) else (
  set "NODE=node"
)
echo Starter Nimay OMP Bridge...
echo.
"%NODE%" omp-bridge.mjs
echo.
echo Broen er stoppet. Trykk en tast for a lukke.
pause >nul
