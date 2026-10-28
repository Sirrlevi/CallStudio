@echo off
setlocal
set VERSION=8.13
set BASE=%USERPROFILE%\.gradle\callstudio-bootstrap
set DIST=%BASE%\gradle-%VERSION%
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%BASE%\gradle-%VERSION%-bin.zip'; Expand-Archive -Path '%BASE%\gradle-%VERSION%-bin.zip' -DestinationPath '%BASE%' -Force; Remove-Item '%BASE%\gradle-%VERSION%-bin.zip'"
  if errorlevel 1 exit /b 1
)
call "%DIST%\bin\gradle.bat" %*
