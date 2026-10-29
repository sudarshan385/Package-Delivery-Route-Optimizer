$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
if (-not $env:PGPASSWORD -and $env:DEV_DB_PASSWORD) { $env:PGPASSWORD = $env:DEV_DB_PASSWORD }
if (-not $env:PGPASSWORD) {
    Write-Error "Database password is not set. Please set PGPASSWORD or DEV_DB_PASSWORD."
    exit 1
}
& $psql -U app_user -h localhost -p 5432 -d packagedeliveryrouteoptimizer -c "SELECT current_database(); SELECT 'users' as tbl, count(*) as count FROM users UNION ALL SELECT 'locations', count(*) FROM locations UNION ALL SELECT 'location_edges', count(*) FROM location_edges UNION ALL SELECT 'packages', count(*) FROM packages UNION ALL SELECT 'deliveries', count(*) FROM deliveries;"
& $psql -U app_user -h localhost -p 5432 -d packagedeliveryrouteoptimizer -c "SELECT id, username, role, active FROM users;"
