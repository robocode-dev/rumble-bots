@echo off
rem Boots Retcon from source (Java 22+ multi-file source launch), following the rumble-bots catalog convention:
rem only the official Bot API jar is fetched, into a cache outside the bot directory.
setlocal
rem Older launchers compile only the entry file and fail with misleading "package does not exist" errors.
set JAVA_SPEC=0
for /f "tokens=2 delims==" %%v in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.specification.version"') do set JAVA_SPEC=%%v
set JAVA_SPEC=%JAVA_SPEC: =%
for /f "tokens=1 delims=." %%m in ("%JAVA_SPEC%") do set JAVA_MAJOR=%%m
if %JAVA_MAJOR% LSS 22 (
  echo Retcon needs Java 22 or newer as 'java' on PATH ^(multi-file source launch^), found java.specification.version=%JAVA_SPEC% 1>&2
  exit /b 1
)
set BOT_API_VERSION=1.3.1
if "%RUMBLE_JAVA_CACHE%"=="" set RUMBLE_JAVA_CACHE=%USERPROFILE%\.cache\rumble-bots-java
if not exist "%RUMBLE_JAVA_CACHE%" mkdir "%RUMBLE_JAVA_CACHE%"
set JAR=%RUMBLE_JAVA_CACHE%\robocode-tankroyale-bot-api-%BOT_API_VERSION%.jar
if not exist "%JAR%" curl -fsSL -o "%JAR%" "https://repo1.maven.org/maven2/dev/robocode/tankroyale/robocode-tankroyale-bot-api/%BOT_API_VERSION%/robocode-tankroyale-bot-api-%BOT_API_VERSION%.jar"
java -cp "%JAR%" "%~dp0src\fnl\bots\retcon\Retcon.java" %*
