$ErrorActionPreference = 'Stop'

# Load the local runtime environment into the current PowerShell session.
# Dot-source this file so variables remain in the caller's shell:
#   . .\scripts\local-env.ps1 auth
# Supported services: auth, customer, account, transfer, notification, audit, gateway.

$repoRoot = Split-Path -Parent $PSScriptRoot
$secretDir = Join-Path $repoRoot '.jwt'
$keystore = Join-Path $secretDir 'jwt-signing.p12'
$publicKey = Join-Path $secretDir 'jwt-public.pem'
$envFile = Join-Path $repoRoot 'infrastructure\.env'
$localService = if ($args.Count -gt 0) { $args[0].ToLowerInvariant() } else { 'auth' }

$validServices = @('auth', 'customer', 'account', 'transfer', 'notification', 'audit', 'gateway')
if ($validServices -notcontains $localService) {
    throw "Unsupported service '$localService'. Use: $($validServices -join ', ')."
}

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw 'keytool was not found. Install a JDK and make sure JAVA_HOME/bin is on PATH.'
}

if (-not (Test-Path $envFile)) {
    throw "Missing $envFile. Copy infrastructure/.env.example to infrastructure/.env and set local values first."
}

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null

# Import infrastructure/.env without overriding values already supplied by the shell.
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line -split '=', 2
        if ($parts.Count -eq 2) {
            $name = $parts[0].Trim()
            $value = $parts[1].Trim().Trim('"').Trim("'")
            if ($name -and -not (Get-Item "Env:$name" -ErrorAction SilentlyContinue)) {
                [Environment]::SetEnvironmentVariable($name, $value)
            }
        }
    }
}

function Set-DefaultEnv([string]$Name, [string]$Value) {
    if (-not (Get-Item "Env:$Name" -ErrorAction SilentlyContinue)) {
        [Environment]::SetEnvironmentVariable($Name, $Value)
    }
}

function Require-Env([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value) -or $value -like 'replace-with-*') {
        throw "Required environment variable '$Name' is missing or still contains a placeholder. Configure infrastructure/.env first."
    }
}

Set-DefaultEnv 'SPRING_PROFILES_ACTIVE' 'dev'
Set-DefaultEnv 'CONFIG_SERVICE_URL' 'http://localhost:8888'
Set-DefaultEnv 'SECURITY_JWT_KEYSTORE_ALIAS' 'jwt-signing'
Set-DefaultEnv 'CUSTOMER_SERVICE_URL' 'http://localhost:8082'
Set-DefaultEnv 'ACCOUNT_SERVICE_URL' 'http://localhost:8083'
Set-DefaultEnv 'AUTH_SERVICE_URL' 'http://localhost:8081'
Set-DefaultEnv 'TRANSFER_SERVICE_URL' 'http://localhost:8084'
Set-DefaultEnv 'NOTIFICATION_SERVICE_URL' 'http://localhost:8085'
Set-DefaultEnv 'AUDIT_SERVICE_URL' 'http://localhost:8086'
Set-DefaultEnv 'FUNDING_ENABLED' 'true'
Set-DefaultEnv 'SPRING_KAFKA_BOOTSTRAPSERVERS' 'localhost:29092'

if (-not $env:SECURITY_JWT_ISSUER -and $env:JWT_ISSUER) { $env:SECURITY_JWT_ISSUER = $env:JWT_ISSUER }
if (-not $env:SECURITY_JWT_AUDIENCE -and $env:JWT_AUDIENCE) { $env:SECURITY_JWT_AUDIENCE = $env:JWT_AUDIENCE }
if (-not $env:SECURITY_JWT_KEYSTORE_PASSWORD -and $env:JWT_KEYSTORE_PASSWORD) { $env:SECURITY_JWT_KEYSTORE_PASSWORD = $env:JWT_KEYSTORE_PASSWORD }
if (-not $env:SECURITY_INTERNALKEY -and $env:INTERNAL_SERVICE_KEY) { $env:SECURITY_INTERNALKEY = $env:INTERNAL_SERVICE_KEY }

Set-DefaultEnv 'SECURITY_JWT_KEYSTORE_PATH' $keystore
Set-DefaultEnv 'SECURITY_JWT_PUBLIC_KEY_PATH' $publicKey

Require-Env 'SECURITY_JWT_ISSUER'
Require-Env 'SECURITY_JWT_AUDIENCE'
Require-Env 'SECURITY_JWT_KEYSTORE_PASSWORD'
Require-Env 'SECURITY_INTERNALKEY'

if ($localService -eq 'auth') {
    Require-Env 'DEMO_ADMIN_USERNAME'
    Require-Env 'DEMO_ADMIN_PASSWORD'
    Require-Env 'DEMO_USER_PASSWORD'
    Require-Env 'OAUTH2_DEMO_CLIENT_SECRET'
}

$databasePasswordByService = @{
    auth = 'DB_PASSWORD_AUTH'
    customer = 'DB_PASSWORD_CUSTOMER'
    account = 'DB_PASSWORD_ACCOUNT'
    transfer = 'DB_PASSWORD_TRANSFER'
    notification = 'DB_PASSWORD_NOTIFICATION'
    audit = 'DB_PASSWORD_AUDIT'
}

if ($localService -ne 'gateway') {
    Require-Env $databasePasswordByService[$localService]
}

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
    'auth' {
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
if ($localService -ne 'gateway') {
    Write-Host "DATASOURCE_URL=$env:DATASOURCE_URL"
    Write-Host "DATASOURCE_USERNAME=$env:DATASOURCE_USERNAME"
}
Write-Host "SPRING_KAFKA_BOOTSTRAPSERVERS=$env:SPRING_KAFKA_BOOTSTRAPSERVERS"
Write-Host "CUSTOMER_SERVICE_URL=$env:CUSTOMER_SERVICE_URL"
Write-Host "ACCOUNT_SERVICE_URL=$env:ACCOUNT_SERVICE_URL"
Write-Host "AUTH_SERVICE_URL=$env:AUTH_SERVICE_URL"
Write-Host "TRANSFER_SERVICE_URL=$env:TRANSFER_SERVICE_URL"
Write-Host "NOTIFICATION_SERVICE_URL=$env:NOTIFICATION_SERVICE_URL"
Write-Host "AUDIT_SERVICE_URL=$env:AUDIT_SERVICE_URL"
if ($localService -eq 'auth') {
    Write-Host "DEMO_ADMIN_USERNAME=$env:DEMO_ADMIN_USERNAME"
}
Write-Host 'Passwords/secrets: loaded from infrastructure/.env (masked)'
Write-Host '========================================'
Write-Host ''
Write-Host "Run: .\gradlew.bat :$localService-service`:bootRun"
