$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$infra = Join-Path $root 'infrastructure'
$envFile = Join-Path $infra '.env'
$secretDir = Join-Path $infra 'secrets'
$keystore = Join-Path $secretDir 'jwt-signing.p12'
$publicKey = Join-Path $secretDir 'jwt-public.pem'

& (Join-Path $PSScriptRoot 'setup-local.ps1')
if (-not (Test-Path $envFile)) { throw 'infrastructure/.env was not created. Configure it and run again.' }

function Load-EnvFile([string]$Path) {
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line -split '=', 2
            if ($parts.Count -eq 2) {
                $name = $parts[0].Trim(); $value = $parts[1].Trim().Trim('"').Trim("'")
                [Environment]::SetEnvironmentVariable($name, $value)
            }
        }
    }
}
Load-EnvFile $envFile

foreach ($name in @('SECURITY_JWT_KEYSTORE_PASSWORD','INTERNAL_SERVICE_KEY','OAUTH2_DEMO_CLIENT_SECRET','DEMO_ADMIN_USERNAME','DEMO_ADMIN_PASSWORD','DEMO_USER_PASSWORD','POSTGRES_PASSWORD','DB_PASSWORD_AUTH','DB_PASSWORD_CUSTOMER','DB_PASSWORD_ACCOUNT','DB_PASSWORD_TRANSFER','DB_PASSWORD_NOTIFICATION','DB_PASSWORD_AUDIT')) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value) -or $value -like 'replace-with-*') { throw "Required environment variable '$name' is missing or still contains a placeholder in infrastructure/.env." }
}

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) { throw 'keytool was not found. Java 21 is required.' }
New-Item -ItemType Directory -Force -Path $secretDir | Out-Null

if (-not (Test-Path $keystore)) {
    & keytool -genkeypair -alias jwt-signing -keyalg RSA -keysize 3072 -validity 3650 -storetype PKCS12 -keystore $keystore -storepass $env:SECURITY_JWT_KEYSTORE_PASSWORD -keypass $env:SECURITY_JWT_KEYSTORE_PASSWORD -dname 'CN=demo-jwt-signing, OU=Security, O=Microservice Platform, C=KH'
    if ($LASTEXITCODE -ne 0) { throw 'Failed to generate JWT signing keystore.' }
}
if (-not (Test-Path $publicKey)) {
    & keytool -exportcert -rfc -alias jwt-signing -keystore $keystore -storepass $env:SECURITY_JWT_KEYSTORE_PASSWORD -file $publicKey
    if ($LASTEXITCODE -ne 0) { throw 'Failed to export JWT public key.' }
}

Push-Location $infra
try {
    Write-Host 'Starting the complete local platform with Docker Compose...'
    & docker compose --env-file .env up --build -d
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host ''
    Write-Host 'Local platform started.'
    Write-Host 'Gateway: http://localhost:8080'
    Write-Host 'Auth:    http://localhost:8081'
    Write-Host 'Config:  http://localhost:8888'
    Write-Host 'Login:   DEMO_ADMIN_USERNAME / DEMO_ADMIN_PASSWORD from infrastructure/.env'
} finally { Pop-Location }
