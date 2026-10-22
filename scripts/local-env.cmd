@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Load the local runtime environment into the current CMD session.
rem Usage:
rem   call scripts\local-env.cmd [auth|customer|account|transfer|notification|audit|gateway]

set "REPO_ROOT=%~dp0.."
set "SECRET_DIR=%REPO_ROOT%\.jwt"
set "KEYSTORE=%SECRET_DIR%\jwt-signing.p12"
set "PUBLIC_KEY=%SECRET_DIR%\jwt-public.pem"
set "ENV_FILE=%REPO_ROOT%\infrastructure\.env"
set "LOCAL_SERVICE=%~1"
if "%LOCAL_SERVICE%"=="" set "LOCAL_SERVICE=auth"

if /I not "%LOCAL_SERVICE%"=="auth" if /I not "%LOCAL_SERVICE%"=="customer" if /I not "%LOCAL_SERVICE%"=="account" if /I not "%LOCAL_SERVICE%"=="transfer" if /I not "%LOCAL_SERVICE%"=="notification" if /I not "%LOCAL_SERVICE%"=="audit" if /I not "%LOCAL_SERVICE%"=="gateway" (
    echo ERROR: Unsupported service "%LOCAL_SERVICE%".
    exit /b 1
)

where keytool >nul 2>&1
if errorlevel 1 (
    echo ERROR: keytool was not found. Install a JDK and add JAVA_HOME\bin to PATH.
    exit /b 1
)

if not exist "%ENV_FILE%" (
    echo ERROR: Missing "%ENV_FILE%".
    echo Copy infrastructure\.env.example to infrastructure\.env and set local values first.
    exit /b 1
)

if not exist "%SECRET_DIR%" mkdir "%SECRET_DIR%"

rem Import infrastructure/.env without overriding values already supplied by the shell.
for /f "usebackq tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    if not "%%A"=="" if not "%%A:~0,1%%"=="#" if not defined %%A set "%%A=%%B"
)

if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=dev"
if not defined CONFIG_SERVICE_URL set "CONFIG_SERVICE_URL=http://localhost:8888"
if not defined SECURITY_JWT_ISSUER if defined JWT_ISSUER set "SECURITY_JWT_ISSUER=%JWT_ISSUER%"
if not defined SECURITY_JWT_AUDIENCE if defined JWT_AUDIENCE set "SECURITY_JWT_AUDIENCE=%JWT_AUDIENCE%"
if not defined SECURITY_JWT_KEYSTORE_PASSWORD if defined JWT_KEYSTORE_PASSWORD set "SECURITY_JWT_KEYSTORE_PASSWORD=%JWT_KEYSTORE_PASSWORD%"
if not defined SECURITY_INTERNALKEY if defined INTERNAL_SERVICE_KEY set "SECURITY_INTERNALKEY=%INTERNAL_SERVICE_KEY%"
if not defined SECURITY_JWT_KEYSTORE_ALIAS set "SECURITY_JWT_KEYSTORE_ALIAS=jwt-signing"
set "SECURITY_JWT_KEYSTORE_PATH=%KEYSTORE%"
set "SECURITY_JWT_PUBLIC_KEY_PATH=%PUBLIC_KEY%"

if not defined CUSTOMER_SERVICE_URL set "CUSTOMER_SERVICE_URL=http://localhost:8082"
if not defined ACCOUNT_SERVICE_URL set "ACCOUNT_SERVICE_URL=http://localhost:8083"
if not defined AUTH_SERVICE_URL set "AUTH_SERVICE_URL=http://localhost:8081"
if not defined TRANSFER_SERVICE_URL set "TRANSFER_SERVICE_URL=http://localhost:8084"
if not defined NOTIFICATION_SERVICE_URL set "NOTIFICATION_SERVICE_URL=http://localhost:8085"
if not defined AUDIT_SERVICE_URL set "AUDIT_SERVICE_URL=http://localhost:8086"
if not defined FUNDING_ENABLED set "FUNDING_ENABLED=true"
if not defined SPRING_KAFKA_BOOTSTRAPSERVERS set "SPRING_KAFKA_BOOTSTRAPSERVERS=localhost:29092"

if not defined SECURITY_JWT_ISSUER goto :missing_jwt_issuer
if not defined SECURITY_JWT_AUDIENCE goto :missing_jwt_audience
if not defined SECURITY_JWT_KEYSTORE_PASSWORD goto :missing_keystore_password
if not defined SECURITY_INTERNALKEY goto :missing_internal_key

if /I "%LOCAL_SERVICE%"=="auth" (
    if not defined DEMO_ADMIN_USERNAME goto :missing_demo_admin_username
    if not defined DEMO_ADMIN_PASSWORD goto :missing_demo_admin_password
    if not defined DEMO_USER_PASSWORD goto :missing_demo_user_password
    if not defined OAUTH2_DEMO_CLIENT_SECRET goto :missing_oauth_secret
)

if /I "%LOCAL_SERVICE%"=="auth" if not defined DB_PASSWORD_AUTH goto :missing_db_auth
if /I "%LOCAL_SERVICE%"=="customer" if not defined DB_PASSWORD_CUSTOMER goto :missing_db_customer
if /I "%LOCAL_SERVICE%"=="account" if not defined DB_PASSWORD_ACCOUNT goto :missing_db_account
if /I "%LOCAL_SERVICE%"=="transfer" if not defined DB_PASSWORD_TRANSFER goto :missing_db_transfer
if /I "%LOCAL_SERVICE%"=="notification" if not defined DB_PASSWORD_NOTIFICATION goto :missing_db_notification
if /I "%LOCAL_SERVICE%"=="audit" if not defined DB_PASSWORD_AUDIT goto :missing_db_audit

set "DATASOURCE_URL="
set "DATASOURCE_USERNAME="
set "DATASOURCE_PASSWORD="
if /I "%LOCAL_SERVICE%"=="customer" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/customer"
    set "DATASOURCE_USERNAME=customer_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_CUSTOMER%"
)
if /I "%LOCAL_SERVICE%"=="account" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/account"
    set "DATASOURCE_USERNAME=account_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_ACCOUNT%"
)
if /I "%LOCAL_SERVICE%"=="transfer" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/transfer"
    set "DATASOURCE_USERNAME=transfer_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_TRANSFER%"
)
if /I "%LOCAL_SERVICE%"=="notification" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/notification"
    set "DATASOURCE_USERNAME=notification_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_NOTIFICATION%"
)
if /I "%LOCAL_SERVICE%"=="audit" (
    set "DATASOURCE_URL=jdbc:postgresql://localhost:5439/audit"
    set "DATASOURCE_USERNAME=audit_user"
    set "DATASOURCE_PASSWORD=%DB_PASSWORD_AUDIT%"
)
if /I "%LOCAL_SERVICE%"=="auth" (
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

rem Return local variables to the caller's CMD session.
endlocal & (
  set "LOCAL_SERVICE=%LOCAL_SERVICE%"
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
if not "%DATASOURCE_URL%"=="" echo DATASOURCE_URL=%DATASOURCE_URL%
if not "%DATASOURCE_USERNAME%"=="" echo DATASOURCE_USERNAME=%DATASOURCE_USERNAME%
echo DATASOURCE_PASSWORD=***************
echo SPRING_KAFKA_BOOTSTRAPSERVERS=%SPRING_KAFKA_BOOTSTRAPSERVERS%
echo CUSTOMER_SERVICE_URL=%CUSTOMER_SERVICE_URL%
echo ACCOUNT_SERVICE_URL=%ACCOUNT_SERVICE_URL%
echo AUTH_SERVICE_URL=%AUTH_SERVICE_URL%
echo TRANSFER_SERVICE_URL=%TRANSFER_SERVICE_URL%
echo NOTIFICATION_SERVICE_URL=%NOTIFICATION_SERVICE_URL%
echo AUDIT_SERVICE_URL=%AUDIT_SERVICE_URL%
if /I "%LOCAL_SERVICE%"=="auth" echo DEMO_ADMIN_USERNAME=%DEMO_ADMIN_USERNAME%
echo Passwords/secrets: loaded from infrastructure/.env (masked)
echo ========================================
echo.
echo Run: gradlew.bat :%LOCAL_SERVICE%-service:bootRun
goto :eof

:missing_jwt_issuer
echo ERROR: SECURITY_JWT_ISSUER is required in infrastructure/.env.
exit /b 1
:missing_jwt_audience
echo ERROR: SECURITY_JWT_AUDIENCE or JWT_AUDIENCE is required in infrastructure/.env.
exit /b 1
:missing_keystore_password
echo ERROR: SECURITY_JWT_KEYSTORE_PASSWORD or JWT_KEYSTORE_PASSWORD is required in infrastructure/.env.
exit /b 1
:missing_internal_key
echo ERROR: SECURITY_INTERNALKEY or INTERNAL_SERVICE_KEY is required in infrastructure/.env.
exit /b 1
:missing_demo_admin_username
echo ERROR: DEMO_ADMIN_USERNAME is required for auth-service.
exit /b 1
:missing_demo_admin_password
echo ERROR: DEMO_ADMIN_PASSWORD is required for auth-service.
exit /b 1
:missing_demo_user_password
echo ERROR: DEMO_USER_PASSWORD is required for auth-service.
exit /b 1
:missing_oauth_secret
echo ERROR: OAUTH2_DEMO_CLIENT_SECRET is required for auth-service.
exit /b 1
:missing_db_auth
echo ERROR: DB_PASSWORD_AUTH is required for auth-service.
exit /b 1
:missing_db_customer
echo ERROR: DB_PASSWORD_CUSTOMER is required for customer-service.
exit /b 1
:missing_db_account
echo ERROR: DB_PASSWORD_ACCOUNT is required for account-service.
exit /b 1
:missing_db_transfer
echo ERROR: DB_PASSWORD_TRANSFER is required for transfer-service.
exit /b 1
:missing_db_notification
echo ERROR: DB_PASSWORD_NOTIFICATION is required for notification-service.
exit /b 1
:missing_db_audit
echo ERROR: DB_PASSWORD_AUDIT is required for audit-service.
exit /b 1
