# Run config-service on Windows. Uses local Gradle if installed; otherwise falls back to Docker Compose.
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$infraDir = Join-Path $root "infrastructure"
$envFile = Join-Path $infraDir ".env"
$composeFile = Join-Path $infraDir "docker-compose.yml"

if (-not (Test-Path $envFile)) {
    Write-Error "Missing infrastructure/.env. Run infrastructure/scripts/init-local.sh from Git Bash or WSL first."
    exit 1
}

# Parse dotenv lines as plain data. Do not execute them as PowerShell.
$values = @{}
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $equals = $line.IndexOf('=')
    $name = $line.Substring(0, $equals).Trim()
    $value = $line.Substring($equals + 1)
    if ($name) { $values[$name] = $value }
}

if (-not $values["CONFIG_SERVER_USERNAME"] -or -not $values["CONFIG_SERVER_PASSWORD"]) {
    Write-Error "CONFIG_SERVER_USERNAME and CONFIG_SERVER_PASSWORD must be set in infrastructure/.env."
    exit 1
}

$env:CONFIG_SERVER_USERNAME = $values["CONFIG_SERVER_USERNAME"]
$env:CONFIG_SERVER_PASSWORD = $values["CONFIG_SERVER_PASSWORD"]
$env:CONFIG_REPO_URI = if ($values["CONFIG_REPO_URI"]) { $values["CONFIG_REPO_URI"] } else { "https://github.com/isldevs/microservice-platform-demo.git" }
$env:CONFIG_REPO_LABEL = if ($values["CONFIG_REPO_LABEL"]) { $values["CONFIG_REPO_LABEL"] } else { "main" }
$env:CONFIG_REPO_SEARCH_PATHS = if ($values["CONFIG_REPO_SEARCH_PATHS"]) { $values["CONFIG_REPO_SEARCH_PATHS"] } else { "config-repo" }
$env:SERVER_PORT = if ($values["SERVER_PORT"]) { $values["SERVER_PORT"] } else { "8888" }

foreach ($name in @("CONFIG_REPO_USERNAME", "CONFIG_REPO_PASSWORD")) {
    if ($values[$name]) {
        [Environment]::SetEnvironmentVariable($name, $values[$name], "Process")
    } else {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
}

Write-Host "Config Server credentials loaded from infrastructure/.env; secret values are not printed."
Write-Host "Target URL: http://localhost:$env:SERVER_PORT/actuator/health"

$gradle = Get-Command gradle -ErrorAction SilentlyContinue
if ($gradle) {
    Push-Location (Join-Path $root "config-service")
    try {
        Write-Host "Gradle found; starting config-service directly."
        & gradle --no-daemon bootRun
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    finally {
        Pop-Location
    }
    exit 0
}

# No Gradle installed: build and run the service in Docker instead.
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Error "Neither Gradle nor Docker was found. Install Gradle 8.10.2 with JDK 21, or install/start Docker Desktop."
    exit 1
}

Write-Host "Gradle was not found; falling back to Docker Compose (no local Gradle install needed)."
& docker compose version
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker Compose is not available. Start/update Docker Desktop or install Docker Compose."
    exit $LASTEXITCODE
}

& docker compose --project-directory $infraDir --env-file $envFile -f $composeFile up --build -d config-service
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker Compose failed to start config-service. Check Docker Desktop and the build output above."
    exit $LASTEXITCODE
}

& docker compose --project-directory $infraDir --env-file $envFile -f $composeFile ps config-service
Write-Host ""
Write-Host "Config Server container has been started. Open http://localhost:$env:SERVER_PORT/actuator/health"
Write-Host "To follow logs, change to the infrastructure directory and run: docker compose logs -f config-service"
