param (
    [string]$DbName = "packagedeliveryrouteoptimizer",
    [string]$DbUser = "app_user",
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432",
    [string]$BackupDir = "C:\backups",
    [int]$RetentionDays = 14
)

# Secure authentication via PGPASSFILE
$env:PGPASSFILE = "C:\backups\pgpass.conf"

if (-not (Test-Path $env:PGPASSFILE)) {
    Write-Error "FATAL: PGPASSFILE not found at $($env:PGPASSFILE). Authentication will fail."
    exit 1
}

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupFile = Join-Path $BackupDir "${DbName}_${Timestamp}.dump"
$PgDump = "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe"
$PgRestore = "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe"

Write-Host "Starting backup of database $DbName to $BackupFile..."
& $PgDump -U $DbUser -h $DbHost -p $DbPort -F c -f $BackupFile $DbName
if ($LASTEXITCODE -ne 0) {
    Write-Error "Backup failed with exit code $LASTEXITCODE"
    exit 1
}

Write-Host "Verifying backup file integrity..."
& $PgRestore --list $BackupFile > $null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Backup verification failed! The dump file may be corrupted."
    Remove-Item $BackupFile -Force -ErrorAction SilentlyContinue
    exit 1
}

Write-Host "Backup completed and verified successfully."

Write-Host "Cleaning up backups older than $RetentionDays days..."
$ExpiredBackups = Get-ChildItem -Path $BackupDir -Filter "${DbName}_*.dump" | Where-Object { $_.CreationTime -lt (Get-Date).AddDays(-$RetentionDays) }
foreach ($old in $ExpiredBackups) {
    Write-Host "Removing expired backup: $($old.Name)"
    Remove-Item $old.FullName -Force
}

Write-Host "Backup process finished."
exit 0
