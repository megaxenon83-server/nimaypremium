@echo off
rem Avinstaller Nimay OMP Bridge v2.9.25
rem Kjør som vanlig bruker (ingen Administrator nødvendig)

setlocal

set "SCRIPT_DIR=%~dp0"
set "POWERSHELL=powershell.exe"
set "SCRIPT=%SCRIPT_DIR%uninstall.ps1"

echo ============================================
echo   Avinstaller Nimay OMP Bridge v2.9.25
echo   Mappe: %LOCALAPPDATA%\Nimay\OMPBridge
echo ============================================
echo.

rem Kjør PowerShell-scriptet
%POWERSHELL% -ExecutionPolicy Bypass -File "%SCRIPT%"

if %ERRORLEVEL% neq 0 (
    echo.
    echo Avinstallasjon feilet med feilkode %ERRORLEVEL%.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Trykk på en tast for å avslutte...
pause >nul
exit /b 0
