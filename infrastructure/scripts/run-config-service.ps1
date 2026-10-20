# Run config-service directly on Windows using the local infrastructure/.env.
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$envFile = Join-Path $root "infrastructure/.env"

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

Push-Location (Join-Path $root "config-service")
try {
    Write-Host "Starting config-service on http://localhost:$env:SERVER_PORT"
    Write-Host "Credentials loaded from infrastructure/.env; secret values are not printed."
    & gradle --no-daemon bootRun
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}
