@echo off
title Del Nimay OMP med telefonen
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  set "NODE=C:\Program Files\nodejs\node.exe"
) else (
  set "NODE=node"
)
start "" /min "%NODE%" "%~dp0tools\apk-server.mjs" --app "%~dp0" --port 7787
timeout /t 2 >nul
start "" http://localhost:7787
echo.
echo APK-serveren kjorer pa port 7787.
echo Skann QR-koden i nettleseren, eller skriv inn adressen pa telefonen.
echo Adressen vises i nettleseren som apnes.
timeout /t 4 >nul
