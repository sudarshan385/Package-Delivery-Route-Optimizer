$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
if (-not $env:PGPASSWORD) {
    Write-Error "Database password is not set. Please set PGPASSWORD before running this script."
    exit 1
}
& $psql -U postgres -h localhost -p 5432 -d route_optimizer_db -c "SELECT id, username, email, role FROM users;"
