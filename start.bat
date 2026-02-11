@echo off
title Homieee Mine Server

echo.
echo ========================================================
echo         HOMIEEE MINE SERVER - LAUNCHER
echo ========================================================
echo   [1] Chay server BINH THUONG (Port 25565)
echo   [2] Chay server TEST (Port 25566)
echo   [3] Chay server voi PORT TUY CHON
echo ========================================================
echo.

set /p choice="Chon che do [1/2/3]: "

if "%choice%"=="1" (
    set SERVER_PORT=25565
    goto start_server
)
if "%choice%"=="2" (
    set SERVER_PORT=25566
    goto start_server
)
if "%choice%"=="3" (
    set /p SERVER_PORT="Nhap port (vd: 25567): "
    goto start_server
)

echo Lua chon khong hop le! Chay mac dinh port 25565...
set SERVER_PORT=25565

:start_server
echo.
echo ========================================================
echo   THONG TIN KET NOI SERVER
echo --------------------------------------------------------
echo   Port: %SERVER_PORT%
echo   Neu choi cung may: localhost:%SERVER_PORT%
echo.
echo   IP Noi bo (LAN):
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do echo      %%a:%SERVER_PORT%
echo.
echo   Voice Chat port: 24454 (UDP)
echo --------------------------------------------------------
echo   Dang khoi dong Minecraft Server...
echo ========================================================
echo.

powershell -Command "(Get-Content 'server.properties') -replace 'server-port=\d+', 'server-port=%SERVER_PORT%' | Set-Content 'server.properties'"

java -Xms4G -Xmx4G ^
    -XX:+UseG1GC ^
    -XX:+ParallelRefProcEnabled ^
    -XX:MaxGCPauseMillis=200 ^
    -XX:+UnlockExperimentalVMOptions ^
    -XX:+DisableExplicitGC ^
    -XX:+AlwaysPreTouch ^
    -XX:G1NewSizePercent=30 ^
    -XX:G1MaxNewSizePercent=40 ^
    -XX:G1HeapRegionSize=8M ^
    -XX:G1ReservePercent=20 ^
    -XX:G1HeapWastePercent=5 ^
    -XX:G1MixedGCCountTarget=4 ^
    -XX:InitiatingHeapOccupancyPercent=15 ^
    -XX:G1MixedGCLiveThresholdPercent=90 ^
    -XX:G1RSetUpdatingPauseTimePercent=5 ^
    -XX:SurvivorRatio=32 ^
    -XX:+PerfDisableSharedMem ^
    -XX:MaxTenuringThreshold=1 ^
    -Dfile.encoding=UTF-8 ^
    -jar paper-1.21.11-100.jar nogui

echo.
echo Server da dung. Nhan phim bat ky de dong...
pause >nul
