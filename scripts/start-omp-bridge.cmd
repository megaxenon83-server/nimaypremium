@echo off
REM Starter OMP-bridgen i bakgrunnen (uten terminalvindu).
setlocal
set "BRIDGE_DIR=C:\Program Files\Nimay OMP\bridge"
if not exist "%BRIDGE_DIR%\omp-bridge.mjs" (
  echo Bridge ikke funnet i %BRIDGE_DIR%
  exit /b 1
)
start "" /B node "%BRIDGE_DIR%\omp-bridge.mjs"
exit /b 0
