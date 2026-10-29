@echo off
setlocal
if "%PGPASSWORD%"=="" (
    echo [ERROR] PGPASSWORD environment variable is not set.
    echo Please set PGPASSWORD before executing database setup.
    exit /b 1
)
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -p 5432 -c "CREATE DATABASE packagedeliveryrouteoptimizer OWNER app_user ENCODING 'UTF8';"
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -p 5432 -c "GRANT ALL PRIVILEGES ON DATABASE packagedeliveryrouteoptimizer TO app_user;"
echo DB packagedeliveryrouteoptimizer created.
endlocal
