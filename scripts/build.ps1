param([Parameter(ValueFromRemainingArguments=$true)][string[]]$GradleTasks = @('buildAll'))
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    & (Join-Path $root 'gradlew.bat') --no-daemon @GradleTasks
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }
