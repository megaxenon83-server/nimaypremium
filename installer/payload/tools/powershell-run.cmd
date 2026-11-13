@echo off
rem Wrapper used by the installer so every helper runs with a sane PowerShell.
powershell.exe -NoProfile -ExecutionPolicy Bypass %*
exit /b %ERRORLEVEL%
