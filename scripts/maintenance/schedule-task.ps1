$Action = New-ScheduledTaskAction -Execute 'PowerShell.exe' -Argument '-ExecutionPolicy Bypass -File "S:\MAJOR PROJECT\backend\scripts\backup-database.ps1"'
$Trigger = New-ScheduledTaskTrigger -Daily -At 2:00am
Register-ScheduledTask -TaskName 'RouteOptimizerBackup' -Action $Action -Trigger $Trigger -User 'NT AUTHORITY\SYSTEM' -RunLevel Highest -Force
