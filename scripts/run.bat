@echo off
setlocal
cd /d "%~dp0\.."
if not exist "out\com\netapps\httpserver\server\HttpServer.class" (
    echo Binaries not found. Running build first...
    call scripts\build.bat
)
echo Starting NetApps HTTP Server...
java -cp out com.netapps.httpserver.server.HttpServer %*
