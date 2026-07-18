@echo off
echo =========================================
echo   Starting Snaplink (Lite Profile)
echo =========================================

echo.
echo Stopping any running containers...
docker-compose down 2>nul
docker stop snaplink-lite-app 2>nul
docker rm snaplink-lite-app 2>nul

echo.
echo Starting Lite Application and Database...
docker-compose -f docker-compose-lite.yml up --build

pause
