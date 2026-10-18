@echo off
rem Starts the Deck Duel server. Usage: scripts\run-server.bat [port] [db-file]
if "%~1"=="" (set PORT=5555) else (set PORT=%~1)
if "%~2"=="" (set DB=data\deck-duel.db) else (set DB=%~2)
cd /d "%~dp0.."
java -jar target\deck-duel.jar server --port %PORT% --db %DB%
