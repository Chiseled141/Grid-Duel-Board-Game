@echo off
rem Runs the load-test bot fleet. Usage: scripts\run-bots.bat [bots] [games] [host] [port]
if "%~1"=="" (set BOTS=8) else (set BOTS=%~1)
if "%~2"=="" (set GAMES=4) else (set GAMES=%~2)
if "%~3"=="" (set HOST=127.0.0.1) else (set HOST=%~3)
if "%~4"=="" (set PORT=5555) else (set PORT=%~4)
cd /d "%~dp0.."
java -jar target\onitama.jar bots --host %HOST% --port %PORT% --bots %BOTS% --games %GAMES%
