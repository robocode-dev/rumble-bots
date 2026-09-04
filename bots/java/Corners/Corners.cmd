@echo off
setlocal
set BOT_API_VERSION=1.1.0
if "%RUMBLE_JAVA_CACHE%"=="" set RUMBLE_JAVA_CACHE=%USERPROFILE%\.cache\rumble-bots-java
if not exist "%RUMBLE_JAVA_CACHE%" mkdir "%RUMBLE_JAVA_CACHE%"
set JAR=%RUMBLE_JAVA_CACHE%\robocode-tankroyale-bot-api-%BOT_API_VERSION%.jar
if not exist "%JAR%" curl -fsSL -o "%JAR%" "https://repo1.maven.org/maven2/dev/robocode/tankroyale/robocode-tankroyale-bot-api/%BOT_API_VERSION%/robocode-tankroyale-bot-api-%BOT_API_VERSION%.jar"
java -cp "%JAR%" "%~dp0src\Corners.java" %*
