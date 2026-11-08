@echo off
rem Adapted from robocode-dev/rumble-bots Python StarterBot (Apache-2.0).
rem Modified: renamed the entry point to MizumoriBot; added attribution comments.
rem See THIRD_PARTY_NOTICES.txt and LICENSE-APACHE-2.0.txt.
setlocal
if "%RUMBLE_PYTHON%"=="" set "RUMBLE_PYTHON=python"
"%RUMBLE_PYTHON%" "%~dp0src\MizumoriBot.py" %*
exit /b %errorlevel%
