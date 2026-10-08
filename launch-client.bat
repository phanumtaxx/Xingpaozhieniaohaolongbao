@echo off
setlocal DisableDelayedExpansion
title Xingpaozhieniaohaolongbao Client
pushd "%~dp0"
if errorlevel 1 goto locationError

rem Prefer the installed JDK 21; changes apply only to this launch.
if exist "%ProgramFiles%\Java\jdk-21\bin\javac.exe" set "JAVA_HOME=%ProgramFiles%\Java\jdk-21"
if not defined JAVA_HOME goto javaError
if not exist "%JAVA_HOME%\bin\javac.exe" goto javaError

echo Launching Xingpaozhieniaohaolongbao Client - Fabric 1.21.5
echo The first launch may download development dependencies.
echo For account login, follow the Microsoft sign-in link DevAuth prints below.
echo Once Minecraft opens, press Right Shift to open the GUI.
echo.
call "%~dp0gradlew.bat" --console=plain runClient -PdevAuth %*
set "XING_EXIT_CODE=%ERRORLEVEL%"
popd
if "%XING_EXIT_CODE%"=="0" exit /b 0
echo.
echo The client could not finish launching. See the error above.
pause
exit /b %XING_EXIT_CODE%

:javaError
echo JDK 21 was not found. Install JDK 21 or set JAVA_HOME to its folder.
popd
pause
exit /b 1

:locationError
echo Could not open the client project folder.
pause
exit /b 1
