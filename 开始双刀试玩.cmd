@echo off
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0test.ps1" -Playtest -Combat
if not errorlevel 1 exit /b 0
pause
exit /b 1
