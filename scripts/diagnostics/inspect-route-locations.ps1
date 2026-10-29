$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
if (-not $env:PGPASSWORD -and $env:DEV_DB_PASSWORD) { $env:PGPASSWORD = $env:DEV_DB_PASSWORD }
if (-not $env:PGPASSWORD) {
    Write-Error "Database password is not set. Please set PGPASSWORD or DEV_DB_PASSWORD."
    exit 1
}
& $psql -U app_user -h localhost -p 5432 -d packagedeliveryrouteoptimizer -c "SELECT id, name, latitude, longitude, active, location_type, address FROM locations WHERE id IN (19, 20, 21);"
