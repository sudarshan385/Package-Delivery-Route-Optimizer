@echo off
setlocal
if "%PGPASSWORD%"=="" if not "%DEV_DB_PASSWORD%"=="" set PGPASSWORD=%DEV_DB_PASSWORD%
if "%PGPASSWORD%"=="" (
    echo [ERROR] Database password is not set. Please set PGPASSWORD or DEV_DB_PASSWORD.
    exit /b 1
)
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U app_user -h localhost -p 5432 -d packagedeliveryrouteoptimizer -c "\dt"
endlocal
