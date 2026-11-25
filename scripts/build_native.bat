@echo off
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"
set "SDK_DIR=%ROOT_DIR%\toolchain\android-sdk"
set "NDK_DIR=%SDK_DIR%\ndk\26.3.11579264"
set "NDK_BUILD=%NDK_DIR%\ndk-build.cmd"
set "JNILIBS_DIR=%ROOT_DIR%\app\src\main\jniLibs"

if not exist "%NDK_BUILD%" (
    echo Error: ndk-build not found at %NDK_BUILD%. Run scripts\setup.bat first.
    exit /b 1
)

echo Building ciadpi (byedpi)...
set "BYEDPI_DIR=%ROOT_DIR%\third_party\byedpi"
call "%NDK_BUILD%" -j%NUMBER_OF_PROCESSORS% ^
    NDK_PROJECT_PATH="%BYEDPI_DIR%" ^
    APP_BUILD_SCRIPT="%BYEDPI_DIR%\Android.mk" ^
    NDK_APPLICATION_MK="%BYEDPI_DIR%\Application.mk" ^
    NDK_OUT="%ROOT_DIR%\toolchain\ndk_out\byedpi" ^
    NDK_LIBS_OUT="%ROOT_DIR%\toolchain\ndk_libs\byedpi" ^
    APP_ABI="arm64-v8a armeabi-v7a x86_64" ^
    APP_PLATFORM=android-26
if errorlevel 1 exit /b 1

echo Building hev-socks5-tunnel...
set "HEV_DIR=%ROOT_DIR%\third_party\hev-socks5-tunnel"
call "%NDK_BUILD%" -j%NUMBER_OF_PROCESSORS% ^
    NDK_PROJECT_PATH="%HEV_DIR%" ^
    APP_BUILD_SCRIPT="%HEV_DIR%\Android.mk" ^
    NDK_APPLICATION_MK="%HEV_DIR%\Application.mk" ^
    NDK_OUT="%ROOT_DIR%\toolchain\ndk_out\hev" ^
    NDK_LIBS_OUT="%ROOT_DIR%\toolchain\ndk_libs\hev" ^
    APP_ABI="arm64-v8a armeabi-v7a x86_64" ^
    APP_PLATFORM=android-26
if errorlevel 1 exit /b 1

for %%A in (arm64-v8a armeabi-v7a x86_64) do (
    if not exist "%JNILIBS_DIR%\%%A" mkdir "%JNILIBS_DIR%\%%A"
    copy /Y "%ROOT_DIR%\toolchain\ndk_libs\byedpi\%%A\ciadpi" "%JNILIBS_DIR%\%%A\libciadpi.so" >nul
    if errorlevel 1 exit /b 1
    copy /Y "%ROOT_DIR%\toolchain\ndk_libs\hev\%%A\libhev-socks5-tunnel.so" "%JNILIBS_DIR%\%%A\libhev-socks5-tunnel.so" >nul
    if errorlevel 1 exit /b 1
)

echo Native binaries copied to app\src\main\jniLibs\
endlocal
