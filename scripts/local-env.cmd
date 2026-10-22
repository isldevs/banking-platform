@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Load the complete local runtime environment into the current CMD session.
rem Usage:
rem   call scripts\local-env.cmd [auth|customer|account|transfer|notification|audit|gateway]
rem If no service is supplied, auth-service datasource settings are used.

set "REPO_ROOT=%~dp0.."
set "SECRET_DIR=%REPO_ROOT%\.jwt"
set "KEYSTORE=%SECRET_DIR%\jwt-signing.p12"
set "PUBLIC_KEY=%SECRET_DIR%\jwt-public.pem"
set "ENV_FILE=%REPO_ROOT%\infrastructure\.env"
set "LOCAL_SERVICE=%~1"
if "%LOCAL_SERVICE%"=="" set "LOCAL_SERVICE=auth"

where keytool >nul 2>&1
if errorlevel 1 (
    echo ERROR: keytool was not found. Install a JDK and add JAVA_HOME\bin to PATH.
    exit /b 1
)

if not exist "%SECRET_DIR%" mkdir "%SECRET_DIR%"

rem Import existing infrastructure/.env values without overriding values already supplied by the shell.
if exist "%ENV_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
        if not "%%A"=="" if not "%%A:~0,1%%"=="#" if not defined %%A set "%%A=%%B"
    )
)

rem Common local defaults.
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=dev"
if not defined CONFIG_SERVICE_URL set "CONFIG_SERVICE_URL=http://localhost:8888"
if not defined SECURITY_JWT_ISSUER if defined JWT_ISSUER set "SECURITY_JWT_ISSUER=%JWT_ISSUER%"
if not defined SECURITY_JWT_ISSUER set "SECURITY_JWT_ISSUER=http://localhost:8081"
if not defined SECURITY_JWT_AUDIENCE if defined JWT_AUDIENCE set "SECURITY_JWT_AUDIENCE=%JWT_AUDIENCE%"
if not defined SECURITY_JWT_AUDIENCE set "SECURITY_JWT_AUDIENCE=api"
if not defined SECURITY_JWT_KEYSTORE_PASSWORD if defined JWT_KEYSTORE_PASSWORD set "SECURITY_JWT_KEYSTORE_PASSWORD=%JWT_KEYSTORE_PASSWORD%"
if not defined SECURITY_JWT_KEYSTORE_PASSWORD set "SECURITY_JWT_KEYSTORE_PASSWORD=changeit"
if not defined SECURITY_JWT_KEYSTORE_ALIAS set "SECURITY_JWT_KEYSTORE_ALIAS=jwt-signing"
set "SECURITY_JWT_KEYSTORE_PATH=%KEYSTORE%"
set "SECURITY_JWT_PUBLIC_KEY_PATH=%PUBLIC_KEY%"

if not defined SECURITY_INTERNALKEY if defined INTERNAL_SERVICE_KEY set "SECURITY_INTERNALKEY=%INTERNAL_SERVICE_KEY%"
if not defined SECURITY_INTERNALKEY set "SECURITY_INTERNALKEY=local-dev-internal-service-key-change-me"
if not defined DEMO_ADMIN_USERNAME set "DEMO_ADMIN_USERNAME=admin"
if not defined DEMO_ADMIN_PASSWORD set "DEMO_ADMIN_PASSWORD=local-dev-admin-password"
if not defined DEMO_USER_PASSWORD set "DEMO_USER_PASSWORD=local-dev-user-password"
if not defined OAUTH2_DEMO_CLIENT_SECRET if defined OAUTH2_DEMO_CLIENT_SECRET set "OAUTH2_DEMO_CLIENT_SECRET=%OAUTH2_DEMO_CLIENT_SECRET%"
if not defined OAUTH2_DEMO_CLIENT_SECRET set "OAUTH2_DEMO_CLIENT_SECRET=local-dev-oauth2-client-secret"
if not defined FUNDING_ENABLED set "FUNDING_ENABLED=true"
if not defined SPRING_KAFKA_BOOTSTRAPSERVERS set "SPRING_KAFKA_BOOTSTRAPSERVERS=localhost:29092"

rem Service endpoints for local host processes.
if not defined CUSTOMER_SERVICE_URL set "CUSTOMER_SERVICE_URL=http://localhost:8082"
if not defined ACCOUNT_SERVICE_URL set "ACCOUNT_SERVICE_URL=http://localhost:8083"
if not defined AUTH_SERVICE_URL set "AUTH_SERVICE_URL=http://localhost:8081"
if not defined TRANSFER_SERVICE_URL set "TRANSFER_SERVICE_URL=http://localhost:8084"
if not defined NOTIFICATION_SERVICE_URL set "NOTIFICATION_SERVICE_URL=http://localhost:8085"
if not defined AUDIT_SERVICE_URL set "AUDIT_SERVICE_URL=http://localhost:8086"

rem Database defaults. Override DATASOURCE_* in the shell when using another database.
if not defined POSTGRES_PASSWORD set "POSTGRES_PASSWORD=local-dev-postgres-password"
if not defined DB_PASSWORD_AUTH set "DB_PASSWORD_AUTH=local-dev-auth-password"
if not defined DB_PASSWORD_CUSTOMER set "DB_PASSWORD_CUSTOMER=local-dev-customer-password"
if not defined DB_PASSWORD_ACCOUNT set "DB_PASSWORD_ACCOUNT=local-dev-account-password"
if not defined DB_PASSWORD_TRANSFER set "DB_PASSWORD_TRANSFER=local-dev-transfer-password"
if not defined DB_PASSWORD_NOTIFICATION set "DB_PASSWORD_NOTIFICATION=local-dev-notification-password"
if not defined DB_PASSWORD_AUDIT set "DB_PASSWORD_AUDIT=local-dev-audit-password"

if /i "%LOCAL_SERVICE%"=="customer" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/customer"
    set "DATASOURCE_USERNAME=customer_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_CUSTOMER%"
) else if /i "%LOCAL_SERVICE%"=="account" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/account"
    set "DATASOURCE_USERNAME=account_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_ACCOUNT%"
) else if /i "%LOCAL_SERVICE%"=="transfer" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/transfer"
    set "DATASOURCE_USERNAME=transfer_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_TRANSFER%"
) else if /i "%LOCAL_SERVICE%"=="notification" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/notification"
    set "DATASOURCE_USERNAME=notification_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_NOTIFICATION%"
) else if /i "%LOCAL_SERVICE%"=="audit" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/audit"
    set "DATASOURCE_USERNAME=audit_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_AUDIT%"
) else (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/auth"
    set "DATASOURCE_USERNAME=auth_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_AUTH%"
)

if not exist "%KEYSTORE%" (
    echo Generating local JWT signing keystore: %KEYSTORE%
    keytool -genkeypair ^
      -alias "%SECURITY_JWT_KEYSTORE_ALIAS%" ^
      -keyalg RSA ^
      -keysize 3072 ^
      -validity 3650 ^
      -storetype PKCS12 ^
      -keystore "%KEYSTORE%" ^
      -storepass "%SECURITY_JWT_KEYSTORE_PASSWORD%" ^
      -keypass "%SECURITY_JWT_KEYSTORE_PASSWORD%" ^
      -dname "CN=demo-jwt-signing, OU=Security, O=Microservice Platform, C=KH"
    if errorlevel 1 (
        echo ERROR: Failed to generate JWT signing keystore.
        exit /b 1
    )
)

if not exist "%PUBLIC_KEY%" (
    echo Exporting local JWT public key: %PUBLIC_KEY%
    keytool -exportcert -rfc ^
      -alias "%SECURITY_JWT_KEYSTORE_ALIAS%" ^
      -keystore "%KEYSTORE%" ^
      -storepass "%SECURITY_JWT_KEYSTORE_PASSWORD%" ^
      -file "%PUBLIC_KEY%"
    if errorlevel 1 (
        echo ERROR: Failed to export JWT public key.
        exit /b 1
    )
)

rem Return every local variable to the caller's CMD session.
endlocal & (
  set "SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%"
  set "CONFIG_SERVICE_URL=%CONFIG_SERVICE_URL%"
  set "SECURITY_JWT_KEYSTORE_PATH=%SECURITY_JWT_KEYSTORE_PATH%"
  set "SECURITY_JWT_KEYSTORE_PASSWORD=%SECURITY_JWT_KEYSTORE_PASSWORD%"
  set "SECURITY_JWT_KEYSTORE_ALIAS=%SECURITY_JWT_KEYSTORE_ALIAS%"
  set "SECURITY_JWT_PUBLIC_KEY_PATH=%SECURITY_JWT_PUBLIC_KEY_PATH%"
  set "SECURITY_JWT_ISSUER=%SECURITY_JWT_ISSUER%"
  set "SECURITY_JWT_AUDIENCE=%SECURITY_JWT_AUDIENCE%"
  set "SECURITY_INTERNALKEY=%SECURITY_INTERNALKEY%"
  set "DEMO_ADMIN_USERNAME=%DEMO_ADMIN_USERNAME%"
  set "DEMO_ADMIN_PASSWORD=%DEMO_ADMIN_PASSWORD%"
  set "DEMO_USER_PASSWORD=%DEMO_USER_PASSWORD%"
  set "OAUTH2_DEMO_CLIENT_SECRET=%OAUTH2_DEMO_CLIENT_SECRET%"
  set "FUNDING_ENABLED=%FUNDING_ENABLED%"
  set "SPRING_KAFKA_BOOTSTRAPSERVERS=%SPRING_KAFKA_BOOTSTRAPSERVERS%"
  set "CUSTOMER_SERVICE_URL=%CUSTOMER_SERVICE_URL%"
  set "ACCOUNT_SERVICE_URL=%ACCOUNT_SERVICE_URL%"
  set "AUTH_SERVICE_URL=%AUTH_SERVICE_URL%"
  set "TRANSFER_SERVICE_URL=%TRANSFER_SERVICE_URL%"
  set "NOTIFICATION_SERVICE_URL=%NOTIFICATION_SERVICE_URL%"
  set "AUDIT_SERVICE_URL=%AUDIT_SERVICE_URL%"
  set "POSTGRES_PASSWORD=%POSTGRES_PASSWORD%"
  set "DB_PASSWORD_AUTH=%DB_PASSWORD_AUTH%"
  set "DB_PASSWORD_CUSTOMER=%DB_PASSWORD_CUSTOMER%"
  set "DB_PASSWORD_ACCOUNT=%DB_PASSWORD_ACCOUNT%"
  set "DB_PASSWORD_TRANSFER=%DB_PASSWORD_TRANSFER%"
  set "DB_PASSWORD_NOTIFICATION=%DB_PASSWORD_NOTIFICATION%"
  set "DB_PASSWORD_AUDIT=%DB_PASSWORD_AUDIT%"
  set "DATASOURCE_URL=%DATASOURCE_URL%"
  set "DATASOURCE_USERNAME=%DATASOURCE_USERNAME%"
  set "DATASOURCE_PASSWORD=%DATASOURCE_PASSWORD%"
)

echo.
echo ========================================
echo Local environment loaded
 echo Service: %LOCAL_SERVICE%
echo ========================================
echo SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%
echo CONFIG_SERVICE_URL=%CONFIG_SERVICE_URL%
echo SECURITY_JWT_KEYSTORE_PATH=%SECURITY_JWT_KEYSTORE_PATH%
echo SECURITY_JWT_PUBLIC_KEY_PATH=%SECURITY_JWT_PUBLIC_KEY_PATH%
echo SECURITY_JWT_KEYSTORE_ALIAS=%SECURITY_JWT_KEYSTORE_ALIAS%
echo SECURITY_JWT_ISSUER=%SECURITY_JWT_ISSUER%
echo SECURITY_JWT_AUDIENCE=%SECURITY_JWT_AUDIENCE%
echo DATASOURCE_URL=%DATASOURCE_URL%
echo DATASOURCE_USERNAME=%DATASOURCE_USERNAME%
echo SPRING_KAFKA_BOOTSTRAPSERVERS=%SPRING_KAFKA_BOOTSTRAPSERVERS%
echo CUSTOMER_SERVICE_URL=%CUSTOMER_SERVICE_URL%
echo ACCOUNT_SERVICE_URL=%ACCOUNT_SERVICE_URL%
echo AUTH_SERVICE_URL=%AUTH_SERVICE_URL%
echo TRANSFER_SERVICE_URL=%TRANSFER_SERVICE_URL%
echo NOTIFICATION_SERVICE_URL=%NOTIFICATION_SERVICE_URL%
echo AUDIT_SERVICE_URL=%AUDIT_SERVICE_URL%
echo DEMO_ADMIN_USERNAME=%DEMO_ADMIN_USERNAME%
echo Passwords/secrets: loaded (masked)
echo ========================================
echo.
echo Run: gradlew.bat :%LOCAL_SERVICE%-service:bootRun
