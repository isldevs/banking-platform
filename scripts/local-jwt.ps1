$ErrorActionPreference = 'Stop'

# Local-only JWT signing setup for auth-service + gateway-service.
# The generated keystore is stored under .local/secrets and is ignored by Git.

$repoRoot = Split-Path -Parent $PSScriptRoot
$secretDir = Join-Path $repoRoot '.local\secrets'
$keystore = Join-Path $secretDir 'jwt-signing.p12'
$gradlew = Join-Path $repoRoot 'gradlew.bat'

$keystorePassword = if ($env:LOCAL_JWT_KEYSTORE_PASSWORD) { $env:LOCAL_JWT_KEYSTORE_PASSWORD } else { 'changeit' }
$keyAlias = if ($env:SECURITY_JWT_KEYSTORE_ALIAS) { $env:SECURITY_JWT_KEYSTORE_ALIAS } else { 'jwt-signing' }
$issuer = if ($env:SECURITY_JWT_ISSUER) { $env:SECURITY_JWT_ISSUER } else { 'http://localhost:8081' }
$audience = if ($env:SECURITY_JWT_AUDIENCE) { $env:SECURITY_JWT_AUDIENCE } else { 'microservice-platform' }

if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat was not found. Run this script from the repository checkout."
}

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool was not found. Install a JDK and make sure JAVA_HOME/bin is on PATH."
}

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null

if (-not (Test-Path $keystore)) {
    Write-Host "Generating local JWT signing keystore: $keystore"
    & keytool -genkeypair `
        -alias $keyAlias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 3650 `
        -storetype PKCS12 `
        -keystore $keystore `
        -storepass $keystorePassword `
        -keypass $keystorePassword `
        -dname 'CN=demo-jwt-signing, OU=Security, O=Microservice Platform, C=KH'

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to generate JWT keystore."
    }
}
else {
    Write-Host "Using existing local JWT keystore: $keystore"
}

$env:SPRING_PROFILES_ACTIVE = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'dev' }
$env:SECURITY_JWT_KEYSTORE_PATH = $keystore
$env:SECURITY_JWT_KEYSTORE_PASSWORD = $keystorePassword
$env:SECURITY_JWT_KEYSTORE_ALIAS = $keyAlias
$env:SECURITY_JWT_ISSUER = $issuer
$env:SECURITY_JWT_AUDIENCE = $audience
$env:DEMO_ADMIN_USERNAME = if ($env:DEMO_ADMIN_USERNAME) { $env:DEMO_ADMIN_USERNAME } else { 'admin' }

Write-Host ''
Write-Host 'Local JWT configuration:'
Write-Host "  Profile : $env:SPRING_PROFILES_ACTIVE"
Write-Host "  Issuer  : $env:SECURITY_JWT_ISSUER"
Write-Host "  Audience: $env:SECURITY_JWT_AUDIENCE"
Write-Host "  Alias   : $env:SECURITY_JWT_KEYSTORE_ALIAS"
Write-Host "  Key file: $env:SECURITY_JWT_KEYSTORE_PATH"
Write-Host ''
Write-Host 'Starting auth-service. Keep this terminal open.'
Write-Host ''

Set-Location $repoRoot
& $gradlew ':auth-service:bootRun'
