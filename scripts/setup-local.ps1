$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$infra = Join-Path $root 'infrastructure'
$envFile = Join-Path $infra '.env'
$example = Join-Path $infra '.env.example'
$secretDir = Join-Path $infra 'secrets'
$keystore = Join-Path $secretDir 'jwt-signing.p12'
$publicKey = Join-Path $secretDir 'jwt-public.pem'

function New-RandomHex([int]$Bytes = 32) {
    $data = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($data)
    return ([Convert]::ToHexString($data)).ToLowerInvariant()
}

function Get-EnvValue([hashtable]$Values, [string]$Name) {
    if ($Values.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($Values[$Name])) {
        return $Values[$Name]
    }
    return $null
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java 21 is required.'
}

$javaVersion = (& java -version 2>&1 | Select-String 'version').ToString()
if ($javaVersion -notmatch '"21[.]') {
    throw "Java 21 is required. Detected: $javaVersion"
}

if (-not (Test-Path $example)) {
    throw "Missing $example"
}

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null

# Start from the committed template only when .env does not exist.
if (-not (Test-Path $envFile)) {
    Copy-Item $example $envFile
    Write-Host 'Created infrastructure/.env from .env.example.'
}

# Read the existing file so real values are preserved. Only placeholders/missing values are replaced.
$values = @{}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line -split '=', 2
        if ($parts.Count -eq 2) {
            $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
        }
    }
}

$generated = @{
    'SECURITY_JWT_ISSUER' = 'http://localhost:8081'
    'SECURITY_JWT_AUDIENCE' = 'api'
    'SECURITY_JWT_KEYSTORE_PASSWORD' = New-RandomHex 24
    'INTERNAL_SERVICE_KEY' = New-RandomHex 32
    'OAUTH2_DEMO_CLIENT_SECRET' = New-RandomHex 32
    'DEMO_ADMIN_USERNAME' = 'admin'
    'DEMO_ADMIN_PASSWORD' = New-RandomHex 18
    'DEMO_USER_USERNAME' = 'user'
    'DEMO_USER_PASSWORD' = New-RandomHex 18
    'POSTGRES_PASSWORD' = New-RandomHex 24
    'DB_PASSWORD_AUTH' = New-RandomHex 24
    'DB_PASSWORD_CUSTOMER' = New-RandomHex 24
    'DB_PASSWORD_ACCOUNT' = New-RandomHex 24
    'DB_PASSWORD_TRANSFER' = New-RandomHex 24
    'DB_PASSWORD_NOTIFICATION' = New-RandomHex 24
    'DB_PASSWORD_AUDIT' = New-RandomHex 24
    'APP_UID' = '1000'
    'APP_GID' = '1000'
    'DB_POOL_MAX_SIZE' = '10'
    'DB_POOL_MIN_IDLE' = '2'
    'DB_POOL_CONNECTION_TIMEOUT' = '2000'
    'DB_POOL_VALIDATION_TIMEOUT' = '1000'
    'DB_POOL_IDLE_TIMEOUT' = '600000'
    'DB_POOL_MAX_LIFETIME' = '1800000'
    'TRACING_OTLP_ENABLED' = 'false'
    'TRACING_SAMPLING_PROBABILITY' = '0.1'
    'OTEL_EXPORTER_OTLP_TRACES_ENDPOINT' = 'http://localhost:4318/v1/traces'
    'SPRING_KAFKA_BOOTSTRAPSERVERS' = 'localhost:29092'
    'DOCKER_JWT_ISSUER' = 'http://auth-service:8081'
}

# Generate actual values for missing or template placeholder values.
foreach ($name in $generated.Keys) {
    $current = Get-EnvValue $values $name
    if ([string]::IsNullOrWhiteSpace($current) -or $current -like 'replace-with-*') {
        $values[$name] = $generated[$name]
    }
}

# Keep older local environments compatible with the normalized variable names.
if ((-not $values.ContainsKey('SECURITY_JWT_ISSUER')) -and $values.ContainsKey('JWT_ISSUER')) {
    $values['SECURITY_JWT_ISSUER'] = $values['JWT_ISSUER']
}
if ((-not $values.ContainsKey('SECURITY_JWT_AUDIENCE')) -and $values.ContainsKey('JWT_AUDIENCE')) {
    $values['SECURITY_JWT_AUDIENCE'] = $values['JWT_AUDIENCE']
}
if ((-not $values.ContainsKey('SECURITY_JWT_KEYSTORE_PASSWORD')) -and $values.ContainsKey('JWT_KEYSTORE_PASSWORD')) {
    $values['SECURITY_JWT_KEYSTORE_PASSWORD'] = $values['JWT_KEYSTORE_PASSWORD']
}

$required = @(
    'SECURITY_JWT_ISSUER', 'SECURITY_JWT_AUDIENCE', 'SECURITY_JWT_KEYSTORE_PASSWORD',
    'INTERNAL_SERVICE_KEY', 'OAUTH2_DEMO_CLIENT_SECRET',
    'DEMO_ADMIN_USERNAME', 'DEMO_ADMIN_PASSWORD', 'DEMO_USER_USERNAME', 'DEMO_USER_PASSWORD',
    'POSTGRES_PASSWORD', 'DB_PASSWORD_AUTH', 'DB_PASSWORD_CUSTOMER', 'DB_PASSWORD_ACCOUNT',
    'DB_PASSWORD_TRANSFER', 'DB_PASSWORD_NOTIFICATION', 'DB_PASSWORD_AUDIT'
)
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace((Get-EnvValue $values $name))) {
        throw "Required configuration '$name' is missing."
    }
}

# Write the complete resolved local environment while preserving the template's organization/comments.
$template = Get-Content $example
$output = foreach ($line in $template) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=') {
        $name = $Matches[1]
        if ($values.ContainsKey($name)) {
            "$name=$($values[$name])"
        } else {
            $line
        }
    } else {
        $line
    }
}

# Add any required variables that were not present in the template.
$present = @{}
$output | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=') { $present[$Matches[1]] = $true }
}
foreach ($name in $required) {
    if (-not $present.ContainsKey($name)) {
        $output += "$name=$($values[$name])"
    }
}
Set-Content -Path $envFile -Value $output -Encoding utf8

# Make the resolved environment available to this process for key generation and optional Docker startup.
foreach ($name in $values.Keys) {
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}

$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if (-not $keytool) {
    throw 'keytool was not found. Java 21 installation must include keytool.'
}

if (-not (Test-Path $keystore)) {
    & keytool -genkeypair -alias jwt-signing -keyalg RSA -keysize 3072 -validity 3650 -storetype PKCS12 -keystore $keystore -storepass $values['SECURITY_JWT_KEYSTORE_PASSWORD'] -keypass $values['SECURITY_JWT_KEYSTORE_PASSWORD'] -dname 'CN=demo-jwt-signing, OU=Security, O=Microservice Platform, C=KH'
    if ($LASTEXITCODE -ne 0) { throw 'Failed to generate JWT signing keystore.' }
}

if (-not (Test-Path $publicKey)) {
    & keytool -exportcert -rfc -alias jwt-signing -keystore $keystore -storepass $values['SECURITY_JWT_KEYSTORE_PASSWORD'] -file $publicKey
    if ($LASTEXITCODE -ne 0) { throw 'Failed to export JWT public certificate.' }
}

Write-Host ''
Write-Host 'Local configuration is ready.'
Write-Host "Environment: $envFile"
Write-Host "JWT signing: $keystore"
Write-Host 'Demo admin:  infrastructure/.env -> DEMO_ADMIN_USERNAME / DEMO_ADMIN_PASSWORD'

# Docker is optional. If it is unavailable, setup still succeeds and leaves the project ready for later startup.
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Host ''
    Write-Host 'Docker is not available. Docker startup skipped.'
    Write-Host 'Start Docker later and run this same script again to start the complete platform.'
    exit 0
}

& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host 'Docker is installed but not running. Docker startup skipped.'
    Write-Host 'Start Docker Desktop later and run this same script again to start the complete platform.'
    exit 0
}

Push-Location $infra
try {
    Write-Host ''
    Write-Host 'Starting the complete local platform with Docker Compose...'
    & docker compose --env-file .env up --build -d
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed to start the local platform.' }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host 'Complete local platform started.'
Write-Host 'Gateway: http://localhost:8080'
Write-Host 'Auth:    http://localhost:8081'
Write-Host 'Config:  http://localhost:8888'
Write-Host 'Login:   DEMO_ADMIN_USERNAME / DEMO_ADMIN_PASSWORD from infrastructure/.env'
