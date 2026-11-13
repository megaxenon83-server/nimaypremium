@echo off
rem Installer Nimay OMP Bridge v2.9.25
rem Kjør som vanlig bruker (ingen Administrator nødvendig)

setlocal

set "SCRIPT_DIR=%~dp0"
set "POWERSHELL=powershell.exe"
set "SCRIPT=%SCRIPT_DIR%Install-OMPBridge.ps1"

echo ============================================
echo   Installer Nimay OMP Bridge v2.9.25
echo   Port: 7778
echo   Auto-start: Oppgavetidplan (ved pålogging)
echo   Mappe: %LOCALAPPDATA%\Nimay\OMPBridge
echo ============================================
echo.

rem Kjør PowerShell-scriptet som vanlig bruker
%POWERSHELL% -ExecutionPolicy Bypass -File "%SCRIPT%" -InstallDir "%LOCALAPPDATA%\Nimay\OMPBridge" -Port 7778

if %ERRORLEVEL% neq 0 (
    echo.
    echo Installasjon feilet med feilkode %ERRORLEVEL%.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Trykk på en tast for å avslutte...
pause >nul
exit /b 0
