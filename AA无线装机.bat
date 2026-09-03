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

echo ============================================================
echo   Pt键盘无线装机（不需数据线）
echo ============================================================
echo.
echo 手机先开：设置 -〉开发者选项 -〉无线调试 （与电脑同一 Wi-Fi）
echo.
"%ADB%" start-server >nul 2>&1

echo 首次配对过的手机可直接输入 N 跳过配对，直接 connect。
choice /c YN /n /m "是否需要先配对（首次连接这台手机）？ (Y/N): "
if errorlevel 2 goto :connect
if errorlevel 1 goto :pair

:pair
echo.
echo 在手机「无线调试」页面点「使用配对码配对设备」，会显示 IP:PORT 和 6 位配对码。
set /p PAIR_ADDR="输入配对用的 IP:PORT: "
set /p PAIR_CODE="输入 6 位配对码: "
"%ADB%" pair !PAIR_ADDR! !PAIR_CODE!
if errorlevel 1 (
  echo [ERROR] 配对失败，请确认手机与电脑同一 Wi-Fi、配对码未过期。
  pause & exit /b 1
)
echo.

:connect
echo 回到「无线调试」主页，记下上方显示的 IP 地址与端口（配对用的那个端口可能不一样）。
set /p CONN_ADDR="输入连接用的 IP:PORT: "
"%ADB%" connect !CONN_ADDR!
if errorlevel 1 (
  echo [ERROR] 连接失败。
  pause & exit /b 1
)
"%ADB%" devices
echo.

echo [1/4] Building debug APK...
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
echo [2/4] Installing APK over Wi-Fi (this can take a bit longer than USB)...
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
echo [3/4] Enabling Pt Keyboard IME...
"%ADB%" shell ime enable %IME_ID%
if errorlevel 1 (
  echo [WARN] Automatic enable failed. Enable Pt Keyboard on the settings screen.
)

echo.
echo [4/4] Launching Pt Keyboard settings...
"%ADB%" shell am start -n %ACTIVITY% >nul 2>&1
if errorlevel 1 (
  echo [WARN] Launch failed. Open Pt Keyboard manually from the app launcher.
)

echo.
echo [DONE] Build and install complete over Wi-Fi. Follow the on-screen guide to select Pt Keyboard.
pause
endlocal
