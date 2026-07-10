@echo off
rem ===========================================================================
rem  NaviCloud surum derleyici (Windows / cmd). APK ve/veya masaustu installer
rem  uretir, ciktilari Masaustu'ne birakir.
rem
rem  Kullanim:  scripts\build-release.bat [all|apk|desktop|check]
rem             (varsayilan: all)
rem
rem  Arac yollari otomatik cozulur:
rem    JBR          : %JBR%            -> C:\Program Files\Android\Android Studio\jbr
rem    jpackage JDK : %NAVICLOUD_JDK%  -> ~/.navicloud-build\jdk21 -> Temp\claude scratchpad
rem    makensis     : %NAVICLOUD_NSIS% -> ~/.navicloud-build\nsis-* -> scratchpad -> PATH
rem ===========================================================================
setlocal enabledelayedexpansion

rem scripts\ -> repo koku
cd /d "%~dp0.."
set "ROOT=%CD%"
set "DESKTOP=%USERPROFILE%\Desktop"

set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=all"

rem --- JBR (Android APK derlemesi icin) ---
if defined JBR (set "JBR_DIR=%JBR%") else (set "JBR_DIR=C:\Program Files\Android\Android Studio\jbr")

rem --- jpackage'li tam JDK (masaustu dagitimi icin; JBR'de jpackage yok) ---
set "JDK_DIR=%NAVICLOUD_JDK%"
if not defined JDK_DIR call :find_jdk

rem --- NSIS makensis (installer icin) ---
set "NSIS_EXE=%NAVICLOUD_NSIS%"
if not defined NSIS_EXE call :find_nsis

if /i "%TARGET%"=="check"   ( call :show_check   & goto :done )
if /i "%TARGET%"=="apk"     ( call :build_apk    & goto :done )
if /i "%TARGET%"=="desktop" ( call :build_desktop & goto :done )
if /i "%TARGET%"=="all"     ( call :build_apk && call :build_desktop & goto :done )

echo Bilinmeyen hedef: %TARGET%   (all ^| apk ^| desktop ^| check)
exit /b 1

:done
if errorlevel 1 ( echo ^>^> HATA ile bitti. & exit /b 1 )
echo ^>^> Bitti.
exit /b 0

rem ---------------------------------------------------------------------------
:build_apk
echo ^>^> Android release APK derleniyor...
if not exist "%JBR_DIR%\bin\java.exe" ( echo    JBR yok: %JBR_DIR% & exit /b 1 )
set "JAVA_HOME=%JBR_DIR%"
"%JBR_DIR%\bin\java.exe" -cp "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain :app:assembleRelease --console=plain
if errorlevel 1 ( echo    Gradle basarisiz ^(APK^) & exit /b 1 )
copy /y "app\build\outputs\apk\release\app-release.apk" "%DESKTOP%\NaviCloud-release.apk" >nul
if errorlevel 1 ( echo    APK kopyalanamadi & exit /b 1 )
echo ^>^> %DESKTOP%\NaviCloud-release.apk
exit /b 0

rem ---------------------------------------------------------------------------
:build_desktop
echo ^>^> Masaustu dagitimi ^(jpackage^) + NSIS installer uretiliyor...
if not defined JDK_DIR ( echo    jpackage'li JDK yok. NAVICLOUD_JDK ile yol ver. & exit /b 1 )
if not exist "%JDK_DIR%\bin\jpackage.exe" ( echo    jpackage yok: %JDK_DIR% & exit /b 1 )
if not defined NSIS_EXE ( echo    makensis yok. NAVICLOUD_NSIS ile yol ver. & exit /b 1 )
if not exist "%NSIS_EXE%" ( echo    makensis yok: %NSIS_EXE% & exit /b 1 )
set "JAVA_HOME=%JDK_DIR%"
"%JDK_DIR%\bin\java.exe" -cp "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain :desktop:createDistributable --console=plain
if errorlevel 1 ( echo    Gradle basarisiz ^(desktop^) & exit /b 1 )
set "APPDIR=%ROOT%\desktop\build\compose\binaries\main\app\NaviCloud"
pushd "desktop\installer"
"%NSIS_EXE%" "-DAPPDIR=%APPDIR%" navicloud.nsi
if errorlevel 1 ( popd & echo    makensis basarisiz & exit /b 1 )
popd
copy /y "desktop\installer\NaviCloud-Setup.exe" "%DESKTOP%\NaviCloud-Setup.exe" >nul
if errorlevel 1 ( echo    Setup kopyalanamadi & exit /b 1 )
echo ^>^> %DESKTOP%\NaviCloud-Setup.exe
exit /b 0

rem ---------------------------------------------------------------------------
:show_check
echo ROOT        : %ROOT%
echo Desktop out : %DESKTOP%
if exist "%JBR_DIR%\bin\java.exe" (set "S=OK") else (set "S=YOK!")
echo JBR         : %JBR_DIR%  !S!
if defined JDK_DIR (
    if exist "%JDK_DIR%\bin\jpackage.exe" (set "S=OK") else (set "S=YOK!")
    echo jpackage JDK: %JDK_DIR%  !S!
) else (
    echo jpackage JDK: ^<bulunamadi^>  YOK!
)
if defined NSIS_EXE (
    if exist "%NSIS_EXE%" (set "S=OK") else (set "S=YOK!")
    echo makensis    : %NSIS_EXE%  !S!
) else (
    echo makensis    : ^<bulunamadi^>  YOK!
)
exit /b 0

rem ---------------------------------------------------------------------------
rem jpackage.exe'yi bilinen yerlerde ara; JDK koku = ...\bin\jpackage.exe'nin iki ustu
:find_jdk
for /f "delims=" %%F in ('dir /b /s "%USERPROFILE%\.navicloud-build\jpackage.exe" 2^>nul') do if not defined JP set "JP=%%F"
if not defined JP for /f "delims=" %%F in ('dir /b /s "%LOCALAPPDATA%\Temp\claude\jpackage.exe" 2^>nul') do if not defined JP set "JP=%%F"
if not defined JP goto :eof
for %%A in ("%JP%") do set "JPBIN=%%~dpA"
set "JPBIN=%JPBIN:~0,-1%"
for %%A in ("%JPBIN%") do set "JDK_DIR=%%~dpA"
set "JDK_DIR=%JDK_DIR:~0,-1%"
goto :eof

rem ---------------------------------------------------------------------------
:find_nsis
for /f "delims=" %%F in ('dir /b /s "%USERPROFILE%\.navicloud-build\makensis.exe" 2^>nul') do if not defined NSIS_EXE set "NSIS_EXE=%%F"
if not defined NSIS_EXE for /f "delims=" %%F in ('dir /b /s "%LOCALAPPDATA%\Temp\claude\makensis.exe" 2^>nul') do if not defined NSIS_EXE set "NSIS_EXE=%%F"
if not defined NSIS_EXE for /f "delims=" %%F in ('where makensis 2^>nul') do if not defined NSIS_EXE set "NSIS_EXE=%%F"
goto :eof
