@echo off
setlocal
if "%PGPASSWORD%"=="" (
    echo [ERROR] PGPASSWORD environment variable is not set.
    echo Please set PGPASSWORD before executing backup.
    exit /b 1
)
"C:\Program Files\PostgreSQL\18\bin\pg_dump.exe" -U postgres -h localhost -p 5432 -F c -f "C:\backups\route_optimizer_db_backup_old.dump" route_optimizer_db
echo Backup completed.
endlocal
