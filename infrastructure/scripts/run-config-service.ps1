# Run config-service on Windows without Docker.
# Uses system Gradle if installed, otherwise the repository's Gradle Wrapper.
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$infraDir = Join-Path $root "infrastructure"
$envFile = Join-Path $infraDir ".env"

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

# Let Spring Boot import the dotenv file directly; normalize the path for Spring resource syntax.
$env:INFRASTRUCTURE_ENV_FILE = ($envFile -replace '\\', '/')
$env:CONFIG_SERVER_USERNAME = $values["CONFIG_SERVER_USERNAME"]
$env:CONFIG_SERVER_PASSWORD = $values["CONFIG_SERVER_PASSWORD"]
$env:SERVER_PORT = if ($values["SERVER_PORT"]) { $values["SERVER_PORT"] } else { "8888" }

$gradle = Get-Command gradle -ErrorAction SilentlyContinue
$wrapper = Join-Path $root "gradlew.bat"
$projectDir = Join-Path $root "config-service"

Write-Host "Config Server credentials loaded from infrastructure/.env; secret values are not printed."
Write-Host "Target URL: http://localhost:$env:SERVER_PORT/actuator/health"

if ($gradle) {
    Write-Host "Using installed Gradle."
    Push-Location $projectDir
    try {
        & gradle --no-daemon bootRun
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    finally {
        Pop-Location
    }
    exit 0
}

if (Test-Path $wrapper) {
    if (-not (Test-Path (Join-Path $root "gradle/wrapper/gradle-wrapper.jar"))) {
        Write-Error "Gradle wrapper JAR is missing. Restore gradle/wrapper/gradle-wrapper.jar from the repository."
        exit 1
    }

    Write-Host "System Gradle was not found; using repository Gradle Wrapper."
    Write-Host "The wrapper may download Gradle 8.13 to your user profile on first run; no administrator rights or Docker are required."
    Write-Host "If your company proxy blocks the Gradle distribution, ask IT for an approved Gradle 8.13 installation/mirror."
    & $wrapper --no-daemon -p $projectDir bootRun
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    exit 0
}

Write-Error "Gradle is not installed and gradlew.bat was not found. Update your checkout with 'git pull origin main'. This launcher does not attempt Docker because company policy may block it."
exit 1
