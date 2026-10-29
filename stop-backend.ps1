# ==============================================================================
# Script: stop-backend.ps1 (Root Forwarder)
# Description: Forwards stop execution to backend\stop-backend.ps1
# ==============================================================================

$targetScript = Join-Path $PSScriptRoot "backend\stop-backend.ps1"
if (-not (Test-Path $targetScript)) {
    Write-Error "[FATAL] Backend stop script not found at '$targetScript'."
    exit 1
}

& $targetScript @args
exit $LASTEXITCODE
