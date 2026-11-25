@echo off
setlocal
set "ROOT_DIR=%~dp0.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"
set "KEYTOOL=%ROOT_DIR%\toolchain\jdk\bin\keytool.exe"
set "KEYSTORE=%ROOT_DIR%\antidpi-release.jks"

if not exist "%KEYTOOL%" (
    echo Error: portable JDK keytool not found at %KEYTOOL%. Run scripts\setup.bat first.
    exit /b 1
)

if exist "%KEYSTORE%" (
    echo Keystore already exists: %KEYSTORE%
    exit /b 0
)

"%KEYTOOL%" -genkeypair -v -keystore "%KEYSTORE%" -alias antidpi -keyalg RSA -keysize 2048 -validity 10000 -storepass antidpi123 -keypass antidpi123 -dname "CN=antiDPI, OU=Mobile, O=antiDPI, L=Local, S=Local, C=US"
if errorlevel 1 exit /b 1
echo Created keystore: %KEYSTORE%
endlocal
