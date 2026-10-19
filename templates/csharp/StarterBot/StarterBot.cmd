@echo off
setlocal
if "%RUMBLE_DOTNET_CACHE%"=="" set "RUMBLE_DOTNET_CACHE=%TEMP%\rumble-bots-dotnet"
dotnet run --project "%~dp0StarterBot.csproj" --artifacts-path "%RUMBLE_DOTNET_CACHE%\StarterBot-%RANDOM%-%RANDOM%" -- %*
exit /b %errorlevel%
