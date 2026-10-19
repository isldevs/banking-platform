param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$GradleTasks = @('buildAll')
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $root
try {
    if (-not $GradleTasks -or $GradleTasks.Count -eq 0) { $GradleTasks = @('buildAll') }
    if (Get-Command gradle -ErrorAction SilentlyContinue) {
        & gradle --no-daemon @GradleTasks
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } elseif (Get-Command docker -ErrorAction SilentlyContinue) {
        & docker run --rm --init -v "${root}:/workspace" -v 'gradle-platform-cache:/home/gradle/.gradle' -w /workspace gradle:8.10.2-jdk21 gradle --no-daemon @GradleTasks
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } else {
        throw 'Install Gradle 8.10.2 + JDK 21 or Docker.'
    }
} finally {
    Pop-Location
}
