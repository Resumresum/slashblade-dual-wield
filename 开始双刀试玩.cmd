@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0test.ps1" -Playtest -Combat
if errorlevel 1 pause
