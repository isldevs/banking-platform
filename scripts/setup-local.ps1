$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$infra = Join-Path $root 'infrastructure'
$envFile = Join-Path $infra '.env'
$example = Join-Path $infra '.env.example'
$secretDir = Join-Path $infra 'secrets'
$keystore = Join-Path $secretDir 'jwt-signing.p12'
$publicKey = Join-Path $secretDir 'jwt-public.pem'

function New-RandomHex([int]$Bytes = 32)
{
    $data = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try
    {
        $rng.GetBytes($data)
    }
    finally
    {
        $rng.Dispose()
    }
    return (($data | ForEach-Object { $_.ToString('x2') }) -join '')
}

function Get-EnvValue([hashtable]$Values, [string]$Name)
{
    if ($Values.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($Values[$Name]))
    {
        return $Values[$Name]
    }
    return $null
}

if (-not (Get-Command java -ErrorAction SilentlyContinue))
{
    throw 'Java 17 is required.'
}

# Windows PowerShell 5.1 promotes native stderr to NativeCommandError. java -version intentionally
# writes its version to stderr, so invoke it through cmd.exe and capture the merged output as text.
$javaVersionOutput = (& cmd.exe /c 'java -version 2>&1' | Out-String).Trim()
if ($javaVersionOutput -notmatch 'version\s+"17\.')
{
    throw "Java 17 is required. Detected:`n$javaVersionOutput"
}

if (-not (Test-Path $example))
{
    throw "Missing $example"
}

# Do not silently generate a new keystore password when an old keystore exists.
# Recover the matching .env instead, or intentionally remove the old credentials.
if (-not (Test-Path $envFile) -and (Test-Path $keystore))
{
    throw 'Found an existing JWT keystore without infrastructure/.env. Restore its matching .env or explicitly reset both files before setup.'
}

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null

if (-not (Test-Path $envFile))
{
    Copy-Item $example $envFile
    Write-Host 'Created infrastructure/.env from .env.example.'
}

$values = @{ }
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#'))
    {
        $parts = $line -split '=', 2
        if ($parts.Count -eq 2)
        {
            $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
        }
    }
}

# Migrate old local variable names before generating anything, so an existing JWT keystore remains usable.
if ([string]::IsNullOrWhiteSpace((Get-EnvValue $values 'SECURITY_JWT_ISSUER')) -and $values.ContainsKey('JWT_ISSUER'))
{
    $values['SECURITY_JWT_ISSUER'] = $values['JWT_ISSUER']
}
if ([string]::IsNullOrWhiteSpace((Get-EnvValue $values 'SECURITY_JWT_AUDIENCE')) -and $values.ContainsKey('JWT_AUDIENCE'))
{
    $values['SECURITY_JWT_AUDIENCE'] = $values['JWT_AUDIENCE']
}
if ([string]::IsNullOrWhiteSpace((Get-EnvValue $values 'SECURITY_JWT_KEYSTORE_PASSWORD')) -and $values.ContainsKey('JWT_KEYSTORE_PASSWORD'))
{
    $values['SECURITY_JWT_KEYSTORE_PASSWORD'] = $values['JWT_KEYSTORE_PASSWORD']
}
if ([string]::IsNullOrWhiteSpace((Get-EnvValue $values 'INTERNAL_SERVICE_KEY')) -and $values.ContainsKey('SECURITY_INTERNALKEY'))
{
    $values['INTERNAL_SERVICE_KEY'] = $values['SECURITY_INTERNALKEY']
}

$generated = @{
    'SECURITY_JWT_ISSUER' = 'http://localhost:8081'
    'SECURITY_JWT_AUDIENCE' = 'api'
    'SECURITY_JWT_KEYSTORE_PASSWORD' = New-RandomHex 24
    'SECURITY_JWT_KEYSTORE_PATH' = $keystore
    'SECURITY_JWT_PUBLIC_KEY_PATH' = $publicKey
    'SECURITY_JWT_KEYSTORE_ALIAS' = 'jwt-signing'
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
    'DB_AUTH_URL' = 'jdbc:postgresql://localhost:5439/auth'
    'DB_AUTH_USERNAME' = 'auth_user'
    'DB_CUSTOMER_URL' = 'jdbc:postgresql://localhost:5439/customer'
    'DB_CUSTOMER_USERNAME' = 'customer_user'
    'DB_ACCOUNT_URL' = 'jdbc:postgresql://localhost:5439/account'
    'DB_ACCOUNT_USERNAME' = 'account_user'
    'DB_TRANSFER_URL' = 'jdbc:postgresql://localhost:5439/transfer'
    'DB_TRANSFER_USERNAME' = 'transfer_user'
    'DB_NOTIFICATION_URL' = 'jdbc:postgresql://localhost:5439/notification'
    'DB_NOTIFICATION_USERNAME' = 'notification_user'
    'DB_AUDIT_URL' = 'jdbc:postgresql://localhost:5439/audit'
    'DB_AUDIT_USERNAME' = 'audit_user'
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

foreach ($name in $generated.Keys)
{
    $current = Get-EnvValue $values $name
    if ([string]::IsNullOrWhiteSpace($current) -or $current -like 'replace-with-*')
    {
        $values[$name] = $generated[$name]
    }
}

# Keep service-specific local credentials aligned with the existing database password variables.
$servicePasswordMap = @{
    'DB_AUTH_PASSWORD' = 'DB_PASSWORD_AUTH'
    'DB_CUSTOMER_PASSWORD' = 'DB_PASSWORD_CUSTOMER'
    'DB_ACCOUNT_PASSWORD' = 'DB_PASSWORD_ACCOUNT'
    'DB_TRANSFER_PASSWORD' = 'DB_PASSWORD_TRANSFER'
    'DB_NOTIFICATION_PASSWORD' = 'DB_PASSWORD_NOTIFICATION'
    'DB_AUDIT_PASSWORD' = 'DB_PASSWORD_AUDIT'
}
foreach ($target in $servicePasswordMap.Keys)
{
    $source = $servicePasswordMap[$target]
    if ([string]::IsNullOrWhiteSpace((Get-EnvValue $values $target)) -or (Get-EnvValue $values $target) -like 'replace-with-*')
    {
        $values[$target] = $values[$source]
    }
}

$required = @(
    'SECURITY_JWT_ISSUER', 'SECURITY_JWT_AUDIENCE', 'SECURITY_JWT_KEYSTORE_PASSWORD',
    'SECURITY_JWT_KEYSTORE_PATH', 'SECURITY_JWT_PUBLIC_KEY_PATH', 'SECURITY_JWT_KEYSTORE_ALIAS',
    'INTERNAL_SERVICE_KEY', 'OAUTH2_DEMO_CLIENT_SECRET',
    'DEMO_ADMIN_USERNAME', 'DEMO_ADMIN_PASSWORD', 'DEMO_USER_USERNAME', 'DEMO_USER_PASSWORD',
    'POSTGRES_PASSWORD', 'DB_PASSWORD_AUTH', 'DB_PASSWORD_CUSTOMER', 'DB_PASSWORD_ACCOUNT',
    'DB_PASSWORD_TRANSFER', 'DB_PASSWORD_NOTIFICATION', 'DB_PASSWORD_AUDIT',
    'DB_AUTH_URL', 'DB_AUTH_USERNAME', 'DB_AUTH_PASSWORD',
    'DB_CUSTOMER_URL', 'DB_CUSTOMER_USERNAME', 'DB_CUSTOMER_PASSWORD',
    'DB_ACCOUNT_URL', 'DB_ACCOUNT_USERNAME', 'DB_ACCOUNT_PASSWORD',
    'DB_TRANSFER_URL', 'DB_TRANSFER_USERNAME', 'DB_TRANSFER_PASSWORD',
    'DB_NOTIFICATION_URL', 'DB_NOTIFICATION_USERNAME', 'DB_NOTIFICATION_PASSWORD',
    'DB_AUDIT_URL', 'DB_AUDIT_USERNAME', 'DB_AUDIT_PASSWORD'
)
foreach ($name in $required)
{
    if ( [string]::IsNullOrWhiteSpace((Get-EnvValue $values $name)))
    {
        throw "Required configuration '$name' is missing."
    }
}

$template = Get-Content $example
$output = foreach ($line in $template)
{
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=')
    {
        $name = $Matches[1]
        if ( $values.ContainsKey($name))
        {
            "$name=$( $values[$name] )"
        }
        else
        {
            $line
        }
    }
    else
    {
        $line
    }
}

$present = @{ }
$output | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=')
    {
        $present[$Matches[1]] = $true
    }
}
foreach ($name in $required)
{
    if (-not $present.ContainsKey($name))
    {
        $output += "$name=$( $values[$name] )"
    }
}
# Windows PowerShell 5.1 uses UTF-8 with BOM for -Encoding utf8.
# Docker Compose .env files should not start with a BOM.
[System.IO.File]::WriteAllLines($envFile, [string[]] $output, [System.Text.UTF8Encoding]::new($false))

foreach ($name in $values.Keys)
{
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
}

$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if (-not $keytool)
{
    throw 'keytool was not found. Java 17 installation must include keytool.'
}

if (-not (Test-Path $keystore))
{
    & keytool -genkeypair -alias $values['SECURITY_JWT_KEYSTORE_ALIAS'] -keyalg RSA -keysize 3072 -validity 3650 -storetype PKCS12 -keystore $keystore -storepass $values['SECURITY_JWT_KEYSTORE_PASSWORD'] -keypass $values['SECURITY_JWT_KEYSTORE_PASSWORD'] -dname 'CN=demo-jwt-signing, OU=Security, O=Microservice Platform, C=KH'
    if ($LASTEXITCODE -ne 0)
    {
        throw 'Failed to generate JWT signing keystore.'
    }
}

if (-not (Test-Path $publicKey))
{
    & keytool -exportcert -rfc -alias $values['SECURITY_JWT_KEYSTORE_ALIAS'] -keystore $keystore -storepass $values['SECURITY_JWT_KEYSTORE_PASSWORD'] -file $publicKey
    if ($LASTEXITCODE -ne 0)
    {
        throw 'Failed to export JWT public certificate.'
    }
}

Write-Host ''
Write-Host 'Local configuration is ready. Docker Compose has NOT been started.'
Write-Host "Environment: $envFile"
Write-Host "JWT signing: $keystore"
Write-Host 'Demo admin: infrastructure/.env -> DEMO_ADMIN_USERNAME / DEMO_ADMIN_PASSWORD'
Write-Host ''
Write-Host 'Start the local platform when Docker is available:'
Write-Host '  cd infrastructure'
Write-Host '  docker compose up --build -d'
Write-Host '  docker compose ps'
Write-Host ''
Write-Host 'After successful startup, access Gateway at http://localhost:8080'
