@echo off
rem Starts the Swing client. Usage: scripts\run-client.bat [host] [port]
if "%~1"=="" (set HOST=127.0.0.1) else (set HOST=%~1)
if "%~2"=="" (set PORT=5555) else (set PORT=%~2)
cd /d "%~dp0.."
java -jar target\onitama.jar client --host %HOST% --port %PORT%
