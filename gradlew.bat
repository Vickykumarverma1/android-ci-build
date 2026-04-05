@echo off
setlocal

set APP_HOME=%~dp0
if exist "%APP_HOME%gradlew-local.bat" (
  call "%APP_HOME%gradlew-local.bat" %*
  exit /b %errorlevel%
)

echo This repository uses a lightweight gradlew script for GitHub/Linux/macOS builds.
echo On Windows, install Gradle locally and run:
echo   gradle %*
exit /b 1
