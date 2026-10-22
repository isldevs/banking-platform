$ErrorActionPreference = 'Stop'

# Dot-source this file in PowerShell so the variables remain in the current shell:
#   . .\scripts\local-env.ps1

$repoRoot = Split-Path -Parent $PSScriptRoot
$secretDir = Join-Path $repoRoot '.local\secrets'
$keystore = Join-Path $secretDir 'jwt-signing.p12'

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw 'keytool was not found. Install a JDK and make sure JAVA_HOME/bin is on PATH.'
}

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null

if (-not (Test-Path $keystore)) {
    $password = if ($env:LOCAL_JWT_KEYSTORE_PASSWORD) { $env:LOCAL_JWT_KEYSTORE_PASSWORD } else { 'changeit' }
    $alias = if ($env:SECURITY_JWT_KEYSTORE_ALIAS) { $env:SECURITY_JWT_KEYSTORE_ALIAS } else { 'jwt-signing' }

    Write-Host "Generating local JWT keystore: $keystore"
    & keytool -genkeypair `
        -alias $alias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 3650 `
        -storetype PKCS12 `
        -keystore $keystore `
        -storepass $password `
        -keypass $password `
        -dname 'CN=demo-jwt-signing, OU=Security, O=Microservice Platform, C=KH'

    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to generate JWT keystore.'
    }
}

$env:SPRING_PROFILES_ACTIVE = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'dev' }
$env:SECURITY_JWT_KEYSTORE_PATH = $keystore
$env:SECURITY_JWT_KEYSTORE_PASSWORD = if ($env:LOCAL_JWT_KEYSTORE_PASSWORD) { $env:LOCAL_JWT_KEYSTORE_PASSWORD } else { 'changeit' }
$env:SECURITY_JWT_KEYSTORE_ALIAS = if ($env:SECURITY_JWT_KEYSTORE_ALIAS) { $env:SECURITY_JWT_KEYSTORE_ALIAS } else { 'jwt-signing' }
$env:SECURITY_JWT_ISSUER = if ($env:SECURITY_JWT_ISSUER) { $env:SECURITY_JWT_ISSUER } else { 'http://localhost:8081' }
$env:SECURITY_JWT_AUDIENCE = if ($env:SECURITY_JWT_AUDIENCE) { $env:SECURITY_JWT_AUDIENCE } else { 'microservice-platform' }
$env:DEMO_ADMIN_USERNAME = if ($env:DEMO_ADMIN_USERNAME) { $env:DEMO_ADMIN_USERNAME } else { 'admin' }

Write-Host ''
Write-Host 'JWT environment variables loaded into the current PowerShell session:'
Get-ChildItem Env: | Where-Object Name -like 'SECURITY_JWT_*' | Sort-Object Name | ForEach-Object {
    if ($_.Name -like '*PASSWORD*') {
        Write-Host "  $($_.Name)=********"
    } else {
        Write-Host "  $($_.Name)=$($_.Value)"
    }
}
Write-Host ''
Write-Host 'Run: .\gradlew.bat :auth-service:bootRun'
Write-Host 'Then, in another shell, load this file there too before starting gateway-service.'
