@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle 9.5.0 is required. Install Gradle or use GitHub Actions to build MarbleMD.
exit /b 2
