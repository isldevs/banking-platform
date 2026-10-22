$ErrorActionPreference = 'Stop'

# Load the complete local runtime environment into the current PowerShell session.
# Dot-source this file so variables remain in the caller's shell:
#   . .\scripts\local-env.ps1 auth
# Supported services: auth, customer, account, transfer, notification, audit, gateway.

$repoRoot = Split-Path -Parent $PSScriptRoot
$secretDir = Join-Path $repoRoot '.jwt'
$keystore = Join-Path $secretDir 'jwt-signing.p12'
$publicKey = Join-Path $secretDir 'jwt-public.pem'
$envFile = Join-Path $repoRoot 'infrastructure\.env'
$localService = if ($args.Count -gt 0) { $args[0].ToLowerInvariant() } else { 'auth' }

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw 'keytool was not found. Install a JDK and make sure JAVA_HOME/bin is on PATH.'
}

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null

# Import infrastructure/.env without overriding values already supplied by the shell.
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line -split '=', 2
            if ($parts.Count -eq 2 -and -not (Get-Item "Env:$($parts[0])" -ErrorAction SilentlyContinue)) {
                [Environment]::SetEnvironmentVariable($parts[0], $parts[1])
            }
        }
    }
}

function Set-DefaultEnv([string]$Name, [string]$Value) {
    if (-not (Get-Item "Env:$Name" -ErrorAction SilentlyContinue)) {
        [Environment]::SetEnvironmentVariable($Name, $Value)
    }
}

Set-DefaultEnv 'SPRING_PROFILES_ACTIVE' 'dev'
Set-DefaultEnv 'CONFIG_SERVICE_URL' 'http://localhost:8888'

if (-not $env:SECURITY_JWT_ISSUER -and $env:JWT_ISSUER) { $env:SECURITY_JWT_ISSUER = $env:JWT_ISSUER }
Set-DefaultEnv 'SECURITY_JWT_ISSUER' 'http://localhost:8081'
if (-not $env:SECURITY_JWT_AUDIENCE -and $env:JWT_AUDIENCE) { $env:SECURITY_JWT_AUDIENCE = $env:JWT_AUDIENCE }
Set-DefaultEnv 'SECURITY_JWT_AUDIENCE' 'api'
if (-not $env:SECURITY_JWT_KEYSTORE_PASSWORD -and $env:JWT_KEYSTORE_PASSWORD) { $env:SECURITY_JWT_KEYSTORE_PASSWORD = $env:JWT_KEYSTORE_PASSWORD }
Set-DefaultEnv 'SECURITY_JWT_KEYSTORE_PASSWORD' 'changeit'
Set-DefaultEnv 'SECURITY_JWT_KEYSTORE_ALIAS' 'jwt-signing'
$env:SECURITY_JWT_KEYSTORE_PATH = $keystore
$env:SECURITY_JWT_PUBLIC_KEY_PATH = $publicKey

if (-not $env:SECURITY_INTERNALKEY -and $env:INTERNAL_SERVICE_KEY) { $env:SECURITY_INTERNALKEY = $env:INTERNAL_SERVICE_KEY }
Set-DefaultEnv 'SECURITY_INTERNALKEY' 'local-dev-internal-service-key-change-me'
Set-DefaultEnv 'DEMO_ADMIN_USERNAME' 'admin'
Set-DefaultEnv 'DEMO_ADMIN_PASSWORD' 'local-dev-admin-password'
Set-DefaultEnv 'DEMO_USER_PASSWORD' 'local-dev-user-password'
Set-DefaultEnv 'OAUTH2_DEMO_CLIENT_SECRET' 'local-dev-oauth2-client-secret'
Set-DefaultEnv 'FUNDING_ENABLED' 'true'
Set-DefaultEnv 'SPRING_KAFKA_BOOTSTRAPSERVERS' 'localhost:29092'

# Service endpoints for local host processes.
Set-DefaultEnv 'CUSTOMER_SERVICE_URL' 'http://localhost:8082'
Set-DefaultEnv 'ACCOUNT_SERVICE_URL' 'http://localhost:8083'
Set-DefaultEnv 'AUTH_SERVICE_URL' 'http://localhost:8081'
Set-DefaultEnv 'TRANSFER_SERVICE_URL' 'http://localhost:8084'
Set-DefaultEnv 'NOTIFICATION_SERVICE_URL' 'http://localhost:8085'
Set-DefaultEnv 'AUDIT_SERVICE_URL' 'http://localhost:8086'

# Local database defaults. Override DATASOURCE_* in the shell when required.
Set-DefaultEnv 'POSTGRES_PASSWORD' 'local-dev-postgres-password'
Set-DefaultEnv 'DB_PASSWORD_AUTH' 'local-dev-auth-password'
Set-DefaultEnv 'DB_PASSWORD_CUSTOMER' 'local-dev-customer-password'
Set-DefaultEnv 'DB_PASSWORD_ACCOUNT' 'local-dev-account-password'
Set-DefaultEnv 'DB_PASSWORD_TRANSFER' 'local-dev-transfer-password'
Set-DefaultEnv 'DB_PASSWORD_NOTIFICATION' 'local-dev-notification-password'
Set-DefaultEnv 'DB_PASSWORD_AUDIT' 'local-dev-audit-password'

switch ($localService) {
    'customer' {
        $env:DATASOURCE_URL = 'jdbc:postgresql://localhost:5439/customer'
        $env:DATASOURCE_USERNAME = 'customer_user'
        $env:DATASOURCE_PASSWORD = $env:DB_PASSWORD_CUSTOMER
    }
    'account' {
        $env:DATASOURCE_URL = 'jdbc:postgresql://localhost:5439/account'
        $env:DATASOURCE_USERNAME = 'account_user'
        $env:DATASOURCE_PASSWORD = $env:DB_PASSWORD_ACCOUNT
    }
    'transfer' {
        $env:DATASOURCE_URL = 'jdbc:postgresql://localhost:5439/transfer'
        $env:DATASOURCE_USERNAME = 'transfer_user'
        $env:DATASOURCE_PASSWORD = $env:DB_PASSWORD_TRANSFER
    }
    'notification' {
        $env:DATASOURCE_URL = 'jdbc:postgresql://localhost:5439/notification'
        $env:DATASOURCE_USERNAME = 'notification_user'
        $env:DATASOURCE_PASSWORD = $env:DB_PASSWORD_NOTIFICATION
    }
    'audit' {
        $env:DATASOURCE_URL = 'jdbc:postgresql://localhost:5439/audit'
        $env:DATASOURCE_USERNAME = 'audit_user'
        $env:DATASOURCE_PASSWORD = $env:DB_PASSWORD_AUDIT
    }
    default {
        $env:DATASOURCE_URL = 'jdbc:postgresql://localhost:5439/auth'
        $env:DATASOURCE_USERNAME = 'auth_user'
        $env:DATASOURCE_PASSWORD = $env:DB_PASSWORD_AUTH
    }
}

if (-not (Test-Path $keystore)) {
    Write-Host "Generating local JWT signing keystore: $keystore"
    & keytool -genkeypair `
        -alias $env:SECURITY_JWT_KEYSTORE_ALIAS `
        -keyalg RSA `
        -keysize 3072 `
        -validity 3650 `
        -storetype PKCS12 `
        -keystore $keystore `
        -storepass $env:SECURITY_JWT_KEYSTORE_PASSWORD `
        -keypass $env:SECURITY_JWT_KEYSTORE_PASSWORD `
        -dname 'CN=demo-jwt-signing, OU=Security, O=Microservice Platform, C=KH'

    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to generate JWT signing keystore.'
    }
}

if (-not (Test-Path $publicKey)) {
    Write-Host "Exporting local JWT public key: $publicKey"
    & keytool -exportcert -rfc `
        -alias $env:SECURITY_JWT_KEYSTORE_ALIAS `
        -keystore $keystore `
        -storepass $env:SECURITY_JWT_KEYSTORE_PASSWORD `
        -file $publicKey

    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to export JWT public key.'
    }
}

Write-Host ''
Write-Host '========================================'
Write-Host "Local environment loaded: $localService"
Write-Host '========================================'
Write-Host "SPRING_PROFILES_ACTIVE=$env:SPRING_PROFILES_ACTIVE"
Write-Host "CONFIG_SERVICE_URL=$env:CONFIG_SERVICE_URL"
Write-Host "SECURITY_JWT_KEYSTORE_PATH=$env:SECURITY_JWT_KEYSTORE_PATH"
Write-Host "SECURITY_JWT_PUBLIC_KEY_PATH=$env:SECURITY_JWT_PUBLIC_KEY_PATH"
Write-Host "SECURITY_JWT_KEYSTORE_ALIAS=$env:SECURITY_JWT_KEYSTORE_ALIAS"
Write-Host "SECURITY_JWT_ISSUER=$env:SECURITY_JWT_ISSUER"
Write-Host "SECURITY_JWT_AUDIENCE=$env:SECURITY_JWT_AUDIENCE"
Write-Host "DATASOURCE_URL=$env:DATASOURCE_URL"
Write-Host "DATASOURCE_USERNAME=$env:DATASOURCE_USERNAME"
Write-Host "SPRING_KAFKA_BOOTSTRAPSERVERS=$env:SPRING_KAFKA_BOOTSTRAPSERVERS"
Write-Host "CUSTOMER_SERVICE_URL=$env:CUSTOMER_SERVICE_URL"
Write-Host "ACCOUNT_SERVICE_URL=$env:ACCOUNT_SERVICE_URL"
Write-Host "AUTH_SERVICE_URL=$env:AUTH_SERVICE_URL"
Write-Host "TRANSFER_SERVICE_URL=$env:TRANSFER_SERVICE_URL"
Write-Host "NOTIFICATION_SERVICE_URL=$env:NOTIFICATION_SERVICE_URL"
Write-Host "AUDIT_SERVICE_URL=$env:AUDIT_SERVICE_URL"
Write-Host "DEMO_ADMIN_USERNAME=$env:DEMO_ADMIN_USERNAME"
Write-Host 'Passwords/secrets: loaded (masked)'
Write-Host '========================================'
Write-Host ''
Write-Host "Run: .\gradlew.bat :$localService-service`:bootRun"
