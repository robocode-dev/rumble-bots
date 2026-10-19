@echo off
setlocal
if "%RUMBLE_PYTHON%"=="" set "RUMBLE_PYTHON=python"
"%RUMBLE_PYTHON%" "%~dp0src\StarterBot.py" %*
exit /b %errorlevel%
