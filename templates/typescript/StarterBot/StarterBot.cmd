@echo off
setlocal
if "%RUMBLE_NODE_CACHE%"=="" set "RUMBLE_NODE_CACHE=%TEMP%\rumble-bots-node"
if not exist "%RUMBLE_NODE_CACHE%" mkdir "%RUMBLE_NODE_CACHE%"
for /f "delims=" %%D in ('node -e "console.log(require('node:fs').mkdtempSync(require('node:path').join(process.argv[1], 'StarterBot-')))" "%RUMBLE_NODE_CACHE%"') do set "RUN_DIR=%%D"
if "%RUN_DIR%"=="" exit /b 1
copy /y "%~dp0package.json" "%RUN_DIR%\package.json" >nul
copy /y "%~dp0package-lock.json" "%RUN_DIR%\package-lock.json" >nul
copy /y "%~dp0StarterBot.json" "%RUN_DIR%\StarterBot.json" >nul
xcopy "%~dp0src\*" "%RUN_DIR%\src" /e /i /q /y >nul
if errorlevel 1 exit /b 1
call npm ci --prefix "%RUN_DIR%" --ignore-scripts --no-audit --no-fund --omit=dev >nul
if errorlevel 1 exit /b 1
node "%RUN_DIR%\src\StarterBot.ts" %*
exit /b %errorlevel%
