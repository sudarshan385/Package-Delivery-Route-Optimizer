$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
Write-Host "--- legacy ---"
if (-not $env:PGPASSWORD) {
    Write-Error "Database password is not set. Please set PGPASSWORD before running this script."
    exit 1
}
& $psql -U postgres -h localhost -p 5432 -d route_optimizer_db -c "SELECT username, role FROM users;"

Write-Host "--- test ---"
$testPass = if ($env:TEST_DB_PASSWORD) { $env:TEST_DB_PASSWORD } else { $env:PGPASSWORD }
$env:PGPASSWORD = $testPass
& $psql -U test_user -h localhost -p 5432 -d route_optimizer_test_db -c "SELECT username, role FROM users;"
