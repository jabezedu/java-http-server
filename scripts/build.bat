@echo off
setlocal
cd /d "%~dp0\.."
if not exist "out" mkdir "out"
echo Compiling java-http-server...
javac -d out -sourcepath src src\com\netapps\httpserver\http\*.java src\com\netapps\httpserver\util\*.java src\com\netapps\httpserver\config\*.java src\com\netapps\httpserver\handler\*.java src\com\netapps\httpserver\server\*.java
if %ERRORLEVEL% equ 0 (
    echo Build successful. Classes compiled to ./out
) else (
    echo Build failed with error code %ERRORLEVEL%
)
