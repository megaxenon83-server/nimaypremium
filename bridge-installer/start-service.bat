@echo off
rem Start service and check status elevated
powershell -Command "Start-Process powershell -Verb RunAs -ArgumentList '-Command sc start NimayOMPBridge; Start-Sleep -Seconds 5; sc query NimayOMPBridge; netstat -ano | findstr 7778' -Wait"
