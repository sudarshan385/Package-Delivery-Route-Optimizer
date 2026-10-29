# Register-RouteOptimizerBackupTask.ps1
# Run from an elevated Administrator PowerShell prompt.
# This script registers the existing backup script as SYSTEM, then runs it once
# immediately and verifies that a new, readable backup was created.

$ErrorActionPreference = 'Stop'

$TaskName = 'RouteOptimizerBackup'
$BackendRoot = 'S:\MAJOR PROJECT\backend'
$BackupScript = Join-Path $BackendRoot 'scripts\backup-database.ps1'
$PassFile = 'C:\backups\pgpass.conf'
$BackupDirectory = 'C:\backups'
$ExpectedDatabase = 'packagedeliveryrouteoptimizer'
$TaskTime = '2:00AM'
$PowerShellExe = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'

function Stop-Safely([string]$Message) {
    throw "SAFETY CHECK FAILED: $Message No task was run by this script after this check."
}

# 1. Require an elevated administrator shell.
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Stop-Safely 'Open PowerShell using Run as administrator, then rerun this script.'
}

# 2. Ensure S: is a local fixed drive. SYSTEM tasks do not reliably see mapped drives.
$drive = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='S:'" -ErrorAction SilentlyContinue
if ($null -eq $drive) {
    Stop-Safely "S: was not detected as a Windows logical disk. Verify the project path and use a local fixed-drive path accessible to SYSTEM."
}
if ($drive.DriveType -ne 3) {
    Stop-Safely "S: is not a local fixed drive (DriveType=$($drive.DriveType)). SYSTEM may not be able to access it. Use an appropriate local fixed-drive path."
}

# 3. Verify all required paths. This script never creates, drops, or modifies a database.
foreach ($path in @($PowerShellExe, $BackupScript, $PassFile, $BackupDirectory)) {
    if (-not (Test-Path -LiteralPath $path)) {
        Stop-Safely "Required path does not exist: $path"
    }
}

$pgRestorePath = $null
if (Test-Path 'C:\Program Files\PostgreSQL\18\bin\pg_restore.exe') {
    $pgRestorePath = 'C:\Program Files\PostgreSQL\18\bin\pg_restore.exe'
} else {
    $cmd = Get-Command 'pg_restore.exe' -ErrorAction SilentlyContinue
    if (-not $cmd) { $cmd = Get-Command 'pg_restore' -ErrorAction SilentlyContinue }
    if ($cmd) { $pgRestorePath = $cmd.Source }
}
if (-not $pgRestorePath -and (Test-Path 'C:\Program Files\PostgreSQL')) {
    $search = Get-ChildItem -Path 'C:\Program Files\PostgreSQL' -Filter 'pg_restore.exe' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($search) { $pgRestorePath = $search.FullName }
}
if (-not $pgRestorePath) {
    Stop-Safely 'pg_restore.exe could not be found. Cannot independently verify backups.'
}


# 4. Ensure the backup script explicitly configures the pgpass file for child pg_dump/pg_restore processes.
$scriptText = [System.IO.File]::ReadAllText($BackupScript)
if ($scriptText -notmatch '(?i)pgpass\.conf') {
    Stop-Safely 'The backup script does not reference pgpass.conf. Inspect/fix its authentication before scheduling.'
}
if ($scriptText -notmatch '(?im)\$env:PGPASSFILE\s*=') {
    Stop-Safely 'The backup script does not explicitly set $env:PGPASSFILE. A SYSTEM task must not rely on the interactive user profile.'
}
if ($scriptText -notmatch [regex]::Escape($ExpectedDatabase)) {
    Stop-Safely "The backup script does not visibly name the expected development database '$ExpectedDatabase'. Verify explicit database targeting before scheduling."
}

# 5. Check SYSTEM has an explicit read allow rule on pgpass.conf. Do not print its contents.
$acl = Get-Acl -LiteralPath $PassFile
$systemSid = 'S-1-5-18'
$systemCanRead = $false
foreach ($rule in $acl.Access) {
    if ($rule.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow) { continue }
    try {
        $sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
    } catch { continue }
    if ($sid -ne $systemSid) { continue }
    $rightsValue = [int]$rule.FileSystemRights
    $readValue = [int][System.Security.AccessControl.FileSystemRights]::Read
    if (($rightsValue -band $readValue) -ne 0) {
        $systemCanRead = $true
        break
    }
}
if (-not $systemCanRead) {
    Stop-Safely 'The pgpass.conf ACL has no explicit SYSTEM read permission. Correct file ACLs without exposing the password, then rerun.'
}
$readBroadPrincipalSids = @('S-1-1-0', 'S-1-5-32-545', 'S-1-5-11') # Everyone, BUILTIN\Users, Authenticated Users
foreach ($rule in $acl.Access) {
    if ($rule.AccessControlType -ne [System.Security.AccessControl.AccessControlType]::Allow) { continue }
    try {
        $sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
    } catch { continue }
    $rightsValue = [int]$rule.FileSystemRights
    $readValue = [int][System.Security.AccessControl.FileSystemRights]::Read
    if ($sid -in $readBroadPrincipalSids -and (($rightsValue -band $readValue) -ne 0)) {
        Stop-Safely 'pgpass.conf grants read access to Everyone, Users, or Authenticated Users. Restrict its NTFS ACL before scheduling.'
    }
}

# 6. Do not overwrite a differently configured pre-existing task.
$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existing) {
    $existingActionText = ($existing.Actions | ForEach-Object { "$($_.Execute) $($_.Arguments)" }) -join ' '
    if ($existingActionText -notlike "*$BackupScript*") {
        Stop-Safely "A task named '$TaskName' already exists but does not point to the expected backup script. Inspect it manually; this installer will not overwrite it."
    }
}

# 7. Register/refresh the task to run under SYSTEM at 02:00 local time.
$arguments = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$BackupScript`""
$action = New-ScheduledTaskAction -Execute $PowerShellExe -Argument $arguments -WorkingDirectory $BackendRoot
$trigger = New-ScheduledTaskTrigger -Daily -At $TaskTime
$taskPrincipal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -WakeToRun -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 30)

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $taskPrincipal -Settings $settings -Description 'Creates and verifies a PostgreSQL backup of packagedeliveryrouteoptimizer daily at 02:00.' -Force | Out-Null

$registered = Get-ScheduledTask -TaskName $TaskName
$registeredInfo = Get-ScheduledTaskInfo -TaskName $TaskName
Write-Host "Registered task: $TaskName" -ForegroundColor Green
Write-Host "Run as: $($registered.Principal.UserId)"
Write-Host "Next scheduled run: $($registeredInfo.NextRunTime)"
Write-Host "Action: $($registered.Actions[0].Execute) $($registered.Actions[0].Arguments)"

# 8. Run one immediate scheduled-task test and wait for completion.
$runStartedAt = Get-Date
$existingBackupsBefore = @(Get-ChildItem -LiteralPath $BackupDirectory -Filter "$ExpectedDatabase`_*.dump" -File -ErrorAction SilentlyContinue)
$latestBefore = $null
if ($existingBackupsBefore.Count -gt 0) {
    $latestBefore = ($existingBackupsBefore | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}

Start-ScheduledTask -TaskName $TaskName
$deadline = (Get-Date).AddMinutes(5)
$observedRun = $false
$finalTask = $null
$finalInfo = $null

do {
    Start-Sleep -Seconds 2
    $finalTask = Get-ScheduledTask -TaskName $TaskName
    $finalInfo = Get-ScheduledTaskInfo -TaskName $TaskName
    if ($finalInfo.LastRunTime -ge $runStartedAt.AddSeconds(-2)) {
        $observedRun = $true
    }
} while ((-not $observedRun -or $finalTask.State -eq 'Running') -and (Get-Date) -lt $deadline)

if (-not $observedRun) {
    Stop-Safely 'The scheduled task did not report a new run within five minutes. Inspect Task Scheduler history and the backup log.'
}
if ($finalTask.State -eq 'Running') {
    Stop-Safely 'The scheduled backup was still running after five minutes. Inspect the task and backup log before rerunning.'
}
if ($finalInfo.LastTaskResult -ne 0) {
    Stop-Safely "The scheduled task completed with LastTaskResult=$($finalInfo.LastTaskResult). Inspect task history and backup logs; do not assume the backup succeeded."
}

# 9. Require a fresh archive and verify it is readable.
$fresh = Get-ChildItem -LiteralPath $BackupDirectory -Filter "$ExpectedDatabase`_*.dump" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -ge $runStartedAt.AddSeconds(-2) } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $fresh) {
    Stop-Safely 'The scheduled task returned success, but no newly written development backup was found.'
}

$restoreOutput = & $pgRestorePath --list $fresh.FullName 2>&1
$restoreExitCode = $LASTEXITCODE
if ($restoreExitCode -ne 0 -or -not $restoreOutput) {
    Stop-Safely "pg_restore --list failed for $($fresh.FullName) with exit code $restoreExitCode. Keep the file for investigation and do not treat this backup as verified."
}

Write-Host ''
Write-Host 'SUCCESS: backup task registration and immediate scheduled run verified.' -ForegroundColor Green
Write-Host "Database target: $ExpectedDatabase"
Write-Host "Backup file: $($fresh.FullName)"
Write-Host "Backup size (bytes): $($fresh.Length)"
Write-Host "Task last run: $($finalInfo.LastRunTime)"
Write-Host "Task result: $($finalInfo.LastTaskResult) (0 means success)"
Write-Host 'Archive listing: PASS (pg_restore --list)'
Write-Host "Next scheduled run: $($finalInfo.NextRunTime)"
Write-Host ''
Write-Host 'Note: daily unattended runs depend on the computer being on/available; StartWhenAvailable requests a missed run when Windows next becomes available.' -ForegroundColor Yellow
