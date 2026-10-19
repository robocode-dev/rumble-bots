@echo off
setlocal
set BOT_API_VERSION=1.3.1
if "%RUMBLE_JAVA_CACHE%"=="" set "RUMBLE_JAVA_CACHE=%TEMP%\rumble-bots-java"
if not exist "%RUMBLE_JAVA_CACHE%" mkdir "%RUMBLE_JAVA_CACHE%"
set "JAR=%RUMBLE_JAVA_CACHE%\robocode-tankroyale-bot-api-%BOT_API_VERSION%.jar"
if not exist "%JAR%" (
  curl -fsSL -o "%JAR%.tmp" "https://repo.maven.apache.org/maven2/dev/robocode/tankroyale/robocode-tankroyale-bot-api/%BOT_API_VERSION%/robocode-tankroyale-bot-api-%BOT_API_VERSION%.jar"
  if errorlevel 1 exit /b 1
  move /y "%JAR%.tmp" "%JAR%" >nul
)
java -cp "%JAR%" "%~dp0src\RumbleTestScout.java" %*
exit /b %errorlevel%
