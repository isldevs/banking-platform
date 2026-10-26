$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$infra = Join-Path $root 'infrastructure'
$envFile = Join-Path $infra '.env'
$example = Join-Path $infra '.env.example'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is required for the local infrastructure.' }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java 21 is required.' }

$javaVersion = (& java -version 2>&1 | Select-String 'version').ToString()
if ($javaVersion -notmatch '"21[.]') { throw "Java 21 is required. Detected: $javaVersion" }

if (-not (Test-Path $envFile)) {
    if (-not (Test-Path $example)) { throw "Missing $example" }
    Copy-Item $example $envFile
    Write-Host "Created infrastructure/.env from .env.example."
    Write-Host 'Set the local values in infrastructure/.env, then run this script again.'
    exit 0
}

$init = Join-Path $infra 'scripts/init-local.sh'
if (Test-Path $init) {
    Write-Host 'Local environment already exists. JWT material will be checked by the start script.'
}

Write-Host 'Local setup is ready.'
Write-Host 'Next: .\scripts\start-local.ps1'
