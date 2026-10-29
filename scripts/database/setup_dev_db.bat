@echo off
setlocal
if "%PGPASSWORD%"=="" (
    echo [ERROR] PGPASSWORD environment variable is not set.
    echo Please set PGPASSWORD before executing database setup.
    exit /b 1
)
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -p 5432 -c "CREATE DATABASE route_optimizer_dev_db OWNER app_user ENCODING 'UTF8';"
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -p 5432 -c "GRANT ALL PRIVILEGES ON DATABASE route_optimizer_dev_db TO app_user;"
echo Dev DB and User created.
endlocal
