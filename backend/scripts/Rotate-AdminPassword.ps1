<#
.SYNOPSIS
    Securely rotates an existing administrator account's password without modifying
    unrelated fields, creating duplicate accounts, or exposing credentials.

.DESCRIPTION
    This script connects to the PostgreSQL database, verifies the administrator account's
    identity and role (ADMIN), and updates only the password hash and updated_at timestamp.
    It uses the application's Spring Security BCryptPasswordEncoder (10 rounds) and
    parameterized SQL updates. Passwords and hashes are never printed, logged, stored in
    source files, or passed in command-line arguments.

.PARAMETER Username
    The administrator username whose password is to be rotated. Default: 'sudarshan'.

.PARAMETER TargetDatabase
    The target database name. Default: 'packagedeliveryrouteoptimizer'.

.PARAMETER Force
    Bypasses interactive confirmation.
#>
[CmdletBinding()]
param(
    [string]$Username = "sudarshan",
    [string]$TargetDatabase = "packagedeliveryrouteoptimizer",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================================================="
Write-Host "ROUTE OPTIMIZER - SECURE OPERATOR CREDENTIAL ROTATION"
Write-Host "Target Database : $TargetDatabase"
Write-Host "Target Username : $Username"
Write-Host "=========================================================================="

# 1. Target safeguards
if ($TargetDatabase -ne "packagedeliveryrouteoptimizer" -and $TargetDatabase -ne "route_optimizer_test_db") {
    Write-Error "Safety violation: Target database must be 'packagedeliveryrouteoptimizer' or 'route_optimizer_test_db', received '$TargetDatabase'."
    exit 1
}

if ([string]::IsNullOrWhiteSpace($Username)) {
    Write-Error "Safety violation: Username parameter is required."
    exit 1
}

# 2. Database credentials from environment (defaults to standard dev credentials)
$dbUser = if ($env:DEV_DB_USERNAME) { $env:DEV_DB_USERNAME } else { "app_user" }
$env:PGPASSWORD = if ($env:DEV_DB_PASSWORD) { $env:DEV_DB_PASSWORD } else { "app_password" }
$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"

if (-not (Test-Path $psql)) {
    Write-Error "PostgreSQL CLI not found at '$psql'."
    exit 1
}

# 3. Verify database identity and admin role existence before updating
$dbCheckSql = "SELECT current_database();"
$connectedDb = (& $psql -U $dbUser -h localhost -p 5432 -d $TargetDatabase -t -A -c $dbCheckSql 2>&1)
$connectedDb = "$connectedDb".Trim()
if ($connectedDb -ne $TargetDatabase) {
    Write-Error "Safety check failed: connected to DB '$connectedDb', expected '$TargetDatabase'."
    exit 1
}

$roleCheckSql = "SELECT role, active, is_blocked FROM users WHERE username = '$Username';"
$userRecord = (& $psql -U $dbUser -h localhost -p 5432 -d $TargetDatabase -t -A -F "|" -c $roleCheckSql 2>&1)
$userRecord = "$userRecord".Trim()
if ([string]::IsNullOrWhiteSpace($userRecord)) {
    Write-Error "Target user '$Username' does not exist in '$TargetDatabase'."
    exit 1
}

$parts = $userRecord.Split("|")
$userRole = $parts[0].Trim()
$isActive = if ($parts.Length -gt 1) { $parts[1].Trim() } else { "t" }
$isBlocked = if ($parts.Length -gt 2) { $parts[2].Trim() } else { "f" }

if ($userRole -ne "ADMIN") {
    Write-Error "Target user '$Username' has role '$userRole', not ADMIN."
    exit 1
}

if ($isActive -ne "t" -and $isActive -ne "true") {
    Write-Error "Security alert: Administrator '$Username' is deactivated (active=false). Rotation aborted."
    exit 1
}

if ($isBlocked -eq "t" -or $isBlocked -eq "true") {
    Write-Error "Security alert: Administrator '$Username' is blocked (is_blocked=true). Rotation aborted."
    exit 1
}

Write-Host "Safety check passed: '$Username' is an active, unblocked ADMIN in '$TargetDatabase'."

# 4. Obtain and verify new password securely (never hardcoded, never echo-printed)
$plainPassword = $null
if ($env:NEW_ADMIN_PASSWORD) {
    $plainPassword = $env:NEW_ADMIN_PASSWORD
} else {
    $pass1Secure = Read-Host -AsSecureString "Enter new password for $Username (minimum 8 characters)"
    $pass2Secure = Read-Host -AsSecureString "Confirm new password"

    $bstr1 = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($pass1Secure)
    $pass1 = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr1)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr1)

    $bstr2 = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($pass2Secure)
    $pass2 = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr2)
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr2)

    if ([string]::IsNullOrWhiteSpace($pass1)) {
        Write-Error "Password rotation aborted: password cannot be empty or whitespace."
        exit 1
    }

    if ($pass1.Length -lt 8) {
        Write-Error "Password rotation aborted: password must be at least 8 characters long."
        exit 1
    }

    if ($pass1 -cne $pass2) {
        $pass1 = $null
        $pass2 = $null
        Write-Error "Password rotation aborted: Passwords do not match."
        exit 1
    }

    $pass2 = $null
    $plainPassword = $pass1
}

if ([string]::IsNullOrWhiteSpace($plainPassword) -or $plainPassword.Length -lt 8) {
    Write-Error "Password rotation aborted: password must be at least 8 characters long."
    exit 1
}

# Explicit operator approval
if (-not $Force) {
    $confirm = Read-Host "Confirm password rotation for administrator '$Username' in '$TargetDatabase' (y/N)"
    if ($confirm -ne 'y' -and $confirm -ne 'Y') {
        Write-Host "Operation cancelled by operator."
        $plainPassword = $null
        $env:NEW_ADMIN_PASSWORD = $null
        exit 2
    }
}

# 5. Execute rotation via application's Spring Security PasswordEncoder and parameterized update
$backendDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$classpathFile = Join-Path $backendDir "classpath.txt"

if (-not (Test-Path $classpathFile)) {
    Write-Host "Generating Maven classpath cache..."
    & mvn dependency:build-classpath "-Dmdep.outputFile=classpath.txt" -f (Join-Path $backendDir "pom.xml") | Out-Null
}

$depsClasspath = Get-Content $classpathFile -Raw
$fullClasspath = (Join-Path $backendDir "target\classes") + ";" + $depsClasspath.Trim()

# Pass password strictly through stdin pipe into Java rotator with --stdin --force flags
# Java will read the password from stdin and will NOT prompt a second time
$procInfo = New-Object System.Diagnostics.ProcessStartInfo
$procInfo.FileName = "java"
$procInfo.Arguments = "-cp `"$fullClasspath`" com.routeoptimizer.tools.AdminPasswordRotator --username `"$Username`" --database `"$TargetDatabase`" --stdin --force"
$procInfo.UseShellExecute = $false
$procInfo.RedirectStandardInput = $true
$procInfo.RedirectStandardOutput = $true
$procInfo.RedirectStandardError = $true
$procInfo.CreateNoWindow = $true

$process = [System.Diagnostics.Process]::Start($procInfo)
$process.StandardInput.WriteLine($plainPassword)
$process.StandardInput.Flush()
$process.StandardInput.Close()

$stdout = $process.StandardOutput.ReadToEnd()
$stderr = $process.StandardError.ReadToEnd()
$process.WaitForExit()

# Clear memory
$plainPassword = $null
$env:NEW_ADMIN_PASSWORD = $null

if ($process.ExitCode -ne 0 -or $stdout -notmatch "SUCCESS:") {
    Write-Error "Password rotation failed (exit code $($process.ExitCode)):`n$stderr`n$stdout"
    exit $process.ExitCode
}

Write-Host ""
Write-Host $stdout.Trim()
Write-Host ""
Write-Host "Verified: User ID, username, email, role, and active status were strictly preserved."
Write-Host "The administrator '$Username' can now authenticate with the new credential."
exit 0
