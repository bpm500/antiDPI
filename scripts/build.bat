@echo off
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"
set "TC_DIR=%ROOT_DIR%\toolchain"

set "JAVA_HOME=%TC_DIR%\jdk"
set "ANDROID_HOME=%TC_DIR%\android-sdk"
set "ANDROID_SDK_ROOT=%TC_DIR%\android-sdk"
set "ANDROID_USER_HOME=%TC_DIR%\android-home"
set "ANDROID_NDK_HOME=%TC_DIR%\android-sdk\ndk\26.3.11579264"
set "GRADLE_USER_HOME=%TC_DIR%\gradle-home"
if not exist "%ANDROID_USER_HOME%" mkdir "%ANDROID_USER_HOME%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\build-tools\34.0.0;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Error: Portable JDK not found. Run scripts\setup.bat first.
    exit /b 1
)

if not exist "%ROOT_DIR%\antidpi-release.jks" (
    call "%ROOT_DIR%\scripts\make_keystore.bat"
    if errorlevel 1 exit /b 1
)

call "%ROOT_DIR%\scripts\build_native.bat"
if errorlevel 1 exit /b 1

if not exist "%ROOT_DIR%\gradlew.bat" (
    call "%TC_DIR%\gradle\bin\gradle.bat" --project-dir "%ROOT_DIR%" wrapper --gradle-version 8.7
    if errorlevel 1 exit /b 1
)

pushd "%ROOT_DIR%"
call "%ROOT_DIR%\gradlew.bat" --no-daemon assembleRelease
if errorlevel 1 (
    popd
    exit /b 1
)
popd

if not exist "%ROOT_DIR%\out" mkdir "%ROOT_DIR%\out"
set "RELEASE_APK=%ROOT_DIR%\app\build\outputs\apk\release\app-release.apk"
if not exist "%RELEASE_APK%" set "RELEASE_APK=%ROOT_DIR%\app\build\outputs\apk\release\app-release-unsigned.apk"

set "APKSIGNER=%ANDROID_HOME%\build-tools\34.0.0\apksigner.bat"
copy /Y "%RELEASE_APK%" "%ROOT_DIR%\out\antiDPI.apk" >nul
call "%APKSIGNER%" sign --ks "%ROOT_DIR%\antidpi-release.jks" --ks-pass pass:antidpi123 --ks-key-alias antidpi --key-pass pass:antidpi123 "%ROOT_DIR%\out\antiDPI.apk"
if errorlevel 1 exit /b 1
call "%APKSIGNER%" verify --verbose "%ROOT_DIR%\out\antiDPI.apk"
if errorlevel 1 exit /b 1

echo Build succeeded: %ROOT_DIR%\out\antiDPI.apk
endlocal
