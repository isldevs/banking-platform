# Run config-service on Windows without Docker. No .env or Config Client credentials are required.
# Uses system Gradle if installed, otherwise the repository's Gradle Wrapper.
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$serverPort = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8888" }
$env:SERVER_PORT = $serverPort
$wrapper = Join-Path $root "gradlew.bat"
$projectDir = Join-Path $root "config-service"
$gradle = Get-Command gradle -ErrorAction SilentlyContinue

Write-Host "Starting Config Server without HTTP Basic credentials."
Write-Host "Target URL: http://localhost:$serverPort/actuator/health"

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

Write-Error "Gradle is not installed and gradlew.bat was not found. This launcher does not attempt Docker because company policy may block it."
exit 1
