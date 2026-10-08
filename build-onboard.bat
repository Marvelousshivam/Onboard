@echo off
setlocal enabledelayedexpansion

rem ============================================================================
rem  build-onboard.bat
rem  ----------------------------------------------------------------------------
rem  Builds the OnBOARD Reader Android application (debug or release APK) using
rem  the project's own Gradle wrapper (gradlew.bat).
rem
rem  Requirements:
rem    - Windows 7+ with cmd.exe.
rem    - Either:
rem        (a) Android Studio installed at the default location with its
rem            bundled JetBrains Runtime (JBR) at:
rem                C:\Program Files\Android\Android Studio\jbr
rem            The script will use this JBR automatically when JAVA_HOME is
rem            not set.
rem        -- OR --
rem        (b) A standalone JDK 17+ with the JAVA_HOME environment variable
rem            pointing at its installation directory.
rem    - The Android SDK must be installed (Android Studio bundles it). If
rem      Android Studio is not installed at the default location, create a
rem      local.properties file in this folder with sdk.dir pointing at your
rem      SDK, or set the ANDROID_HOME environment variable.
rem
rem  Usage:
rem    build-onboard.bat            Builds the debug APK (default).
rem    build-onboard.bat debug      Builds the debug APK (explicit).
rem    build-onboard.bat release    Builds the release APK.
rem
rem  Output APK (on success):
rem    Debug:   app\build\outputs\apk\debug\app-debug.apk
rem    Release: app\build\outputs\apk\release\app-release.apk
rem
rem  Safety notes:
rem    - This script does NOT download, install, or execute any third-party
rem      tools. It only invokes the project's gradlew.bat.
rem    - It does NOT persist any changes to the system environment. If it sets
rem      JAVA_HOME for the Android Studio JBR, that change only lasts for the
rem      current script session.
rem ============================================================================

set "SCRIPT_DIR=%~dp0"
rem Strip the trailing backslash so concatenation produces clean paths.
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "GRADLEW=%SCRIPT_DIR%\gradlew.bat"
set "JBR_PATH=C:\Program Files\Android\Android Studio\jbr"

rem ---------------------------------------------------------------------------
rem Parse the optional first argument: "debug" (default) or "release".
rem ---------------------------------------------------------------------------
set "VARIANT=debug"
if not "%~1"=="" (
    if /i "%~1"=="release" (
        set "VARIANT=release"
    ) else if /i "%~1"=="debug" (
        set "VARIANT=debug"
    ) else (
        echo [ERROR] Unknown argument: "%~1"
        echo         Usage: build-onboard.bat [debug ^| release]
        exit /b 2
    )
)

echo ============================================================================
echo   OnBOARD Reader - Build Script
echo   Variant : !VARIANT!
echo   Project : %SCRIPT_DIR%
echo ============================================================================

rem ---------------------------------------------------------------------------
rem Step 1/4: Verify gradlew.bat exists in the script directory.
rem ---------------------------------------------------------------------------
echo [1/4] Checking for gradlew.bat ...
if not exist "%GRADLEW%" (
    echo [ERROR] gradlew.bat was not found at:
    echo         %GRADLEW%
    echo         Please run this script from the project root directory
    echo         (the folder that contains gradlew.bat).
    exit /b 1
)
echo       Found: %GRADLEW%

rem ---------------------------------------------------------------------------
rem Step 2/4: Resolve a usable JDK.
rem   - Prefer an existing, valid JAVA_HOME.
rem   - If JAVA_HOME is unset or invalid, fall back to the Android Studio JBR.
rem   - If neither is available, abort with a clear explanation.
rem ---------------------------------------------------------------------------
echo [2/4] Resolving JDK ...

set "JAVA_OK=0"
if defined JAVA_HOME (
    if exist "!JAVA_HOME!\bin\java.exe" (
        echo       Using JAVA_HOME: !JAVA_HOME!
        set "JAVA_OK=1"
    ) else (
        echo [WARN]  JAVA_HOME is set but does not point at a valid JDK:
        echo         !JAVA_HOME!
        echo         Will try the Android Studio JBR instead.
    )
)

if "!JAVA_OK!"=="0" (
    if exist "%JBR_PATH%\bin\java.exe" (
        echo       JAVA_HOME was not set. Using Android Studio JBR for this session:
        echo         !JBR_PATH!
        set "JAVA_HOME=!JBR_PATH!"
        set "JAVA_OK=1"
    )
)

if "!JAVA_OK!"=="0" (
    echo [ERROR] No usable JDK was found.
    echo         Either:
    echo           ^(a^) Install Android Studio at the default location
    echo               ^(C:\Program Files\Android\Android Studio^) and this
    echo               script will use its bundled JBR automatically.
    echo           -- OR --
    echo           ^(b^) Install JDK 17+ and set the JAVA_HOME environment
    echo               variable to its installation directory, e.g.:
    echo                 set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17
    echo         Then re-run this script.
    exit /b 1
)

rem ---------------------------------------------------------------------------
rem Step 3/4: Run the Gradle build via the project wrapper.
rem ---------------------------------------------------------------------------
if /i "!VARIANT!"=="release" (
    set "GRADLE_TASK=:app:assembleRelease"
    set "APK_PATH=%SCRIPT_DIR%\app\build\outputs\apk\release\app-release.apk"
) else (
    set "GRADLE_TASK=:app:assembleDebug"
    set "APK_PATH=%SCRIPT_DIR%\app\build\outputs\apk\debug\app-debug.apk"
)

echo [3/4] Running Gradle: gradlew.bat !GRADLE_TASK!
echo       JAVA_HOME=!JAVA_HOME!
echo ----------------------------------------------------------------------------
call "%GRADLEW%" !GRADLE_TASK!
set "GRADLE_EXIT=!ERRORLEVEL!"
echo ----------------------------------------------------------------------------
echo [3/4] Gradle finished with exit code !GRADLE_EXIT!.

if not "!GRADLE_EXIT!"=="0" (
    echo [ERROR] Build failed. Gradle exit code: !GRADLE_EXIT!
    echo         Please review the Gradle output above for the underlying
    echo         cause. Common fixes:
    echo           - Make sure the Android SDK is installed and either
    echo             ANDROID_HOME is set or local.properties contains sdk.dir.
    echo           - Re-run with a clean build:
    echo               gradlew.bat clean ^&^& build-onboard.bat !VARIANT!
    echo           - For release builds, ensure signing is configured in
    echo             app\build.gradle.kts or supply a keystore.
    exit /b !GRADLE_EXIT!
)

rem ---------------------------------------------------------------------------
rem Step 4/4: Verify the APK was produced and offer to open its folder.
rem ---------------------------------------------------------------------------
echo [4/4] Verifying APK output ...
if not exist "%APK_PATH%" (
    echo [WARN] Gradle reported success, but the expected APK was not found:
    echo         %APK_PATH%
    echo         Look inside app\build\outputs\apk\ for the actual artifact.
    exit /b 1
)

echo       APK built successfully:
echo         %APK_PATH%
echo ============================================================================
echo   Build complete.
echo ============================================================================

set "OPEN_FOLDER="
set /p "OPEN_FOLDER=Open the containing folder in Explorer? [Y/N]: "
if /i "!OPEN_FOLDER!"=="Y" (
    explorer /select,"%APK_PATH%"
)

endlocal
exit /b 0
