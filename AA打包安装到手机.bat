@echo off
setlocal enabledelayedexpansion

cd /d %~dp0

set "PACKAGE=com.chacha.jadeime"
set "IME_ID=com.chacha.jadeime/.ime.JadeImeService"
set "ACTIVITY=com.chacha.jadeime/.settings.SettingsActivity"
set "APK=app\build\outputs\apk\debug\app-debug.apk"

rem ---- Resolve Gradle / adb: project wrapper + local.properties > env vars > PATH > legacy default ----
set "GRADLE=%~dp0gradlew.bat"
set "ADB="
set "ANDROID_SDK="
if exist "%~dp0local.properties" (
  for /f "usebackq tokens=1,* delims==" %%a in ("%~dp0local.properties") do (
    if "%%a"=="sdk.dir" set "ANDROID_SDK=%%b"
  )
)
if defined ANDROID_SDK set "ANDROID_SDK=!ANDROID_SDK:\\=\!"
if defined ANDROID_SDK set "ANDROID_SDK=!ANDROID_SDK:\:=:!"

if defined ANDROID_SDK if exist "!ANDROID_SDK!\platform-tools\adb.exe" set "ADB=!ANDROID_SDK!\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB for /f "delims=" %%i in ('where adb.exe 2^>nul') do if not defined ADB set "ADB=%%i"
if not defined ADB if exist "D:\soft3\AndroidSDK\platform-tools\adb.exe" set "ADB=D:\soft3\AndroidSDK\platform-tools\adb.exe"

if not exist "%GRADLE%" (
  echo [ERROR] gradlew.bat not found: %GRADLE%
  pause & exit /b 1
)
if not defined ADB (
  echo [ERROR] adb not found. Set sdk.dir in local.properties, or ANDROID_HOME, or add adb to PATH.
  pause & exit /b 1
)
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JAVA_HOME is invalid: %JAVA_HOME%
  pause & exit /b 1
)
if not defined JAVA_HOME if exist "D:\soft3\AndroidStudio\jbr\bin\java.exe" set "JAVA_HOME=D:\soft3\AndroidStudio\jbr"

echo [1/5] Waiting for device...
"%ADB%" start-server >nul 2>&1
"%ADB%" wait-for-device
if errorlevel 1 (
  echo [ERROR] No device found.
  pause & exit /b 1
)
"%ADB%" devices

echo.
echo [2/5] Building debug APK...
call "%GRADLE%" assembleDebug
if errorlevel 1 (
  echo [ERROR] Build failed.
  pause & exit /b 1
)
if not exist "%APK%" (
  echo [ERROR] APK not found: %APK%
  pause & exit /b 1
)

echo.
echo [3/5] Installing APK...
rem --no-streaming: push the whole APK to the device first, then install from
rem the on-device copy, instead of streaming it live to the package installer.
rem The lexicon.db asset makes this a ~33MB debug APK, which is big enough that
rem streaming install can stall/corrupt over a flaky USB connection.
"%ADB%" install --no-streaming -r "%APK%"
if errorlevel 1 (
  echo [WARN] Install failed, trying uninstall + reinstall...
  choice /c YN /n /m "Uninstall old version and reinstall? (Y/N): "
  if errorlevel 2 (
    echo Cancelled.
    pause & exit /b 1
  )
  "%ADB%" uninstall %PACKAGE%
  "%ADB%" install --no-streaming "%APK%"
  if errorlevel 1 (
    echo [ERROR] Reinstall failed.
    pause & exit /b 1
  )
)

echo.
echo [4/5] Enabling Pt Keyboard IME...
"%ADB%" shell ime enable %IME_ID%
if errorlevel 1 (
  echo [WARN] Automatic enable failed. Enable Pt Keyboard on the settings screen.
)

echo.
echo [5/5] Launching Pt Keyboard settings...
"%ADB%" shell am start -n %ACTIVITY% >nul 2>&1
if errorlevel 1 (
  echo [WARN] Launch failed. Open Pt Keyboard manually from the app launcher.
)

echo.
echo [DONE] Build and install complete. Follow the on-screen guide to select Pt Keyboard.
pause
endlocal
