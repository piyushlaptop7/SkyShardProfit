@echo off
setlocal

echo SkyShard Profit - Fabric 26.1.2 build
echo This project requires Java 25 and Gradle 9.6 or newer.
echo.
java -version
if errorlevel 1 (
  echo Java was not found. Install a Java 25 JDK and add it to PATH.
  exit /b 1
)

gradle --version
if errorlevel 1 (
  echo Gradle was not found. Install Gradle 9.6 or newer and add it to PATH.
  exit /b 1
)

echo.
echo Building mod...
gradle build
if errorlevel 1 (
  echo Build failed. See the Gradle error above.
  exit /b 1
)

echo.
echo Build complete: build\libs\skyshardprofit-1.0.0.jar
endlocal
