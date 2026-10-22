@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Run this file with CALL from CMD so variables remain in the current shell:
rem   call scripts\local-env.cmd

set "REPO_ROOT=%~dp0.."
set "SECRET_DIR=%REPO_ROOT%\.local\secrets"
set "KEYSTORE=%SECRET_DIR%\jwt-signing.p12"

where keytool >nul 2>&1
if errorlevel 1 (
    echo ERROR: keytool was not found. Install a JDK and add JAVA_HOME\bin to PATH.
    exit /b 1
)

if not exist "%SECRET_DIR%" mkdir "%SECRET_DIR%"

if not exist "%KEYSTORE%" (
    echo Generating local JWT keystore: %KEYSTORE%
    if "%LOCAL_JWT_KEYSTORE_PASSWORD%"=="" set "LOCAL_JWT_KEYSTORE_PASSWORD=changeit"
    if "%SECURITY_JWT_KEYSTORE_ALIAS%"=="" set "SECURITY_JWT_KEYSTORE_ALIAS=jwt-signing"

    keytool -genkeypair ^
      -alias "%SECURITY_JWT_KEYSTORE_ALIAS%" ^
      -keyalg RSA ^
      -keysize 2048 ^
      -validity 3650 ^
      -storetype PKCS12 ^
      -keystore "%KEYSTORE%" ^
      -storepass "%LOCAL_JWT_KEYSTORE_PASSWORD%" ^
      -keypass "%LOCAL_JWT_KEYSTORE_PASSWORD%" ^
      -dname "CN=demo-jwt-signing, OU=Security, O=Microservice Platform, C=KH"

    if errorlevel 1 (
        echo ERROR: Failed to generate JWT keystore.
        exit /b 1
    )
)

if "%SPRING_PROFILES_ACTIVE%"=="" set "SPRING_PROFILES_ACTIVE=dev"
set "SECURITY_JWT_KEYSTORE_PATH=%KEYSTORE%"
if "%LOCAL_JWT_KEYSTORE_PASSWORD%"=="" set "LOCAL_JWT_KEYSTORE_PASSWORD=changeit"
set "SECURITY_JWT_KEYSTORE_PASSWORD=%LOCAL_JWT_KEYSTORE_PASSWORD%"
if "%SECURITY_JWT_KEYSTORE_ALIAS%"=="" set "SECURITY_JWT_KEYSTORE_ALIAS=jwt-signing"
if "%SECURITY_JWT_ISSUER%"=="" set "SECURITY_JWT_ISSUER=http://localhost:8081"
if "%SECURITY_JWT_AUDIENCE%"=="" set "SECURITY_JWT_AUDIENCE=microservice-platform"
if "%DEMO_ADMIN_USERNAME%"=="" set "DEMO_ADMIN_USERNAME=admin"

endlocal & (
  set "SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%"
  set "SECURITY_JWT_KEYSTORE_PATH=%SECURITY_JWT_KEYSTORE_PATH%"
  set "SECURITY_JWT_KEYSTORE_PASSWORD=%SECURITY_JWT_KEYSTORE_PASSWORD%"
  set "SECURITY_JWT_KEYSTORE_ALIAS=%SECURITY_JWT_KEYSTORE_ALIAS%"
  set "SECURITY_JWT_ISSUER=%SECURITY_JWT_ISSUER%"
  set "SECURITY_JWT_AUDIENCE=%SECURITY_JWT_AUDIENCE%"
  set "DEMO_ADMIN_USERNAME=%DEMO_ADMIN_USERNAME%"
)

echo.
echo JWT environment variables loaded into the current CMD session:
echo   SECURITY_JWT_KEYSTORE_PATH=%SECURITY_JWT_KEYSTORE_PATH%
echo   SECURITY_JWT_KEYSTORE_PASSWORD=********
echo   SECURITY_JWT_KEYSTORE_ALIAS=%SECURITY_JWT_KEYSTORE_ALIAS%
echo   SECURITY_JWT_ISSUER=%SECURITY_JWT_ISSUER%
echo   SECURITY_JWT_AUDIENCE=%SECURITY_JWT_AUDIENCE%
echo.
echo Run: gradlew.bat :auth-service:bootRun
