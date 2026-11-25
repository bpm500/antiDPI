@echo off
setlocal enabledelayedexpansion

set "ROOT_DIR=%~dp0.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"
set "TC_DIR=%ROOT_DIR%\toolchain"
set "JDK_DIR=%TC_DIR%\jdk"
set "SDK_DIR=%TC_DIR%\android-sdk"
set "GRADLE_HOME=%TC_DIR%\gradle-home"
set "DL_DIR=%TC_DIR%\downloads"

if not exist "%TC_DIR%" mkdir "%TC_DIR%"
if not exist "%SDK_DIR%" mkdir "%SDK_DIR%"
if not exist "%GRADLE_HOME%" mkdir "%GRADLE_HOME%"
if not exist "%DL_DIR%" mkdir "%DL_DIR%"

echo [1/5] Checking portable JDK 17...
if not exist "%JDK_DIR%\bin\java.exe" (
    echo Downloading Eclipse Temurin JDK 17...
    curl.exe -L --fail --retry 3 -o "%DL_DIR%\jdk17.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
    if errorlevel 1 exit /b 1
    if exist "%TC_DIR%\jdk_tmp" rmdir /s /q "%TC_DIR%\jdk_tmp"
    mkdir "%TC_DIR%\jdk_tmp"
    tar.exe -xf "%DL_DIR%\jdk17.zip" -C "%TC_DIR%\jdk_tmp"
    if errorlevel 1 exit /b 1
    for /d %%D in ("%TC_DIR%\jdk_tmp\*") do (
        move "%%D" "%JDK_DIR%" >nul
    )
    rmdir /s /q "%TC_DIR%\jdk_tmp"
)
set "JAVA_HOME=%JDK_DIR%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version
if errorlevel 1 exit /b 1

echo [2/5] Checking Android SDK command-line tools...
set "CMDTOOLS_BIN=%SDK_DIR%\cmdline-tools\latest\bin"
if not exist "%CMDTOOLS_BIN%\sdkmanager.bat" (
    echo Downloading Android command-line tools...
    curl.exe -L --fail --retry 3 -o "%DL_DIR%\cmdline-tools.zip" "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    if errorlevel 1 (
        echo Retrying with latest commandlinetools build...
        curl.exe -L --fail --retry 3 -o "%DL_DIR%\cmdline-tools.zip" "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip"
        if errorlevel 1 exit /b 1
    )
    if exist "%TC_DIR%\cmd_tmp" rmdir /s /q "%TC_DIR%\cmd_tmp"
    mkdir "%TC_DIR%\cmd_tmp"
    tar.exe -xf "%DL_DIR%\cmdline-tools.zip" -C "%TC_DIR%\cmd_tmp"
    if errorlevel 1 exit /b 1
    if not exist "%SDK_DIR%\cmdline-tools" mkdir "%SDK_DIR%\cmdline-tools"
    if exist "%SDK_DIR%\cmdline-tools\latest" rmdir /s /q "%SDK_DIR%\cmdline-tools\latest"
    move "%TC_DIR%\cmd_tmp\cmdline-tools" "%SDK_DIR%\cmdline-tools\latest" >nul
    rmdir /s /q "%TC_DIR%\cmd_tmp"
)

set "ANDROID_HOME=%SDK_DIR%"
set "ANDROID_SDK_ROOT=%SDK_DIR%"
set "PATH=%CMDTOOLS_BIN%;%SDK_DIR%\platform-tools;%PATH%"

echo [3/5] Accepting Android SDK licenses and installing packages...
(
    for /l %%i in (1,1,100) do @echo y
) > "%DL_DIR%\yes.txt"
call "%CMDTOOLS_BIN%\sdkmanager.bat" --sdk_root="%SDK_DIR%" --licenses < "%DL_DIR%\yes.txt" >nul
call "%CMDTOOLS_BIN%\sdkmanager.bat" --sdk_root="%SDK_DIR%" "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.3.11579264" "cmake;3.22.1" < "%DL_DIR%\yes.txt"
if errorlevel 1 exit /b 1

if exist "%SDK_DIR%\ndk\26.3.11579264" (
    if not exist "%TC_DIR%\ndk" (
        mklink /J "%TC_DIR%\ndk" "%SDK_DIR%\ndk\26.3.11579264" >nul 2>&1
    )
)

echo [4/5] Checking portable Gradle 8.7 distribution (for wrapper)...
if not exist "%TC_DIR%\gradle\bin\gradle.bat" (
    echo Downloading Gradle 8.7...
    curl.exe -L --fail --retry 3 -o "%DL_DIR%\gradle.zip" "https://services.gradle.org/distributions/gradle-8.7-bin.zip"
    if errorlevel 1 exit /b 1
    if exist "%TC_DIR%\gradle_tmp" rmdir /s /q "%TC_DIR%\gradle_tmp"
    mkdir "%TC_DIR%\gradle_tmp"
    tar.exe -xf "%DL_DIR%\gradle.zip" -C "%TC_DIR%\gradle_tmp"
    if errorlevel 1 exit /b 1
    for /d %%D in ("%TC_DIR%\gradle_tmp\gradle-*") do (
        move "%%D" "%TC_DIR%\gradle" >nul
    )
    rmdir /s /q "%TC_DIR%\gradle_tmp"
)

echo [5/5] Toolchain summary:
echo   JAVA_HOME=%JAVA_HOME%
echo   ANDROID_HOME=%ANDROID_HOME%
echo   ANDROID_NDK_HOME=%SDK_DIR%\ndk\26.3.11579264
echo   GRADLE_USER_HOME=%GRADLE_HOME%
echo Setup completed successfully!
endlocal
