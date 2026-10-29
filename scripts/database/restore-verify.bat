@echo off
setlocal
if "%PGPASSWORD%"=="" (
    echo [ERROR] PGPASSWORD environment variable is not set.
    echo Please set PGPASSWORD before executing restore verification.
    exit /b 1
)
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -p 5432 -c "DROP DATABASE IF EXISTS restore_verify_db;"
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -p 5432 -c "CREATE DATABASE restore_verify_db OWNER app_user;"
"C:\Program Files\PostgreSQL\18\bin\pg_restore.exe" -U app_user -h localhost -p 5432 -d restore_verify_db "C:\backups\route_optimizer_dev_db_20261009_223001.dump"
echo Restore verification DB populated.
endlocal
