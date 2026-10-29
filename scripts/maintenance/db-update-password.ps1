# ==============================================================================
# Script: db-update-password.ps1
# Description: Administrative password update utility for development database.
# Note: Legacy psql command-line password arguments and hardcoded credentials
#       have been eliminated. This utility safely delegates to the canonical
#       Rotate-AdminPassword.ps1 utility, which passes secrets strictly through
#       standard input into Spring Security's BCryptPasswordEncoder.
# ==============================================================================

$ErrorActionPreference = "Stop"

$expectedDb = "packagedeliveryrouteoptimizer"
$targetUser = "sudarshan"

$rotatorScript = Join-Path $PSScriptRoot "..\..\backend\scripts\Rotate-AdminPassword.ps1"
if (-not (Test-Path $rotatorScript)) {
    Write-Error "ABORT: Canonical secure password rotator script not found at '$rotatorScript'."
    exit 1
}

Write-Host "Delegating to secure administrator credential rotator..."
& $rotatorScript -Username $targetUser -TargetDatabase $expectedDb
exit $LASTEXITCODE

