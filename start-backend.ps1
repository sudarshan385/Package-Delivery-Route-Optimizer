# ==============================================================================
# Script: start-backend.ps1 (Root Forwarder)
# Description: Forwards startup execution to backend\start-backend.ps1
# ==============================================================================

$targetScript = Join-Path $PSScriptRoot "backend\start-backend.ps1"
if (-not (Test-Path $targetScript)) {
    Write-Error "[FATAL] Backend startup script not found at '$targetScript'."
    exit 1
}

& $targetScript @args
exit $LASTEXITCODE
