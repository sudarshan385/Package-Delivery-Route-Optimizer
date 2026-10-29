$ErrorActionPreference = "Stop"
if (-not $env:PGPASSWORD -and $env:DEV_DB_PASSWORD) { $env:PGPASSWORD = $env:DEV_DB_PASSWORD }
if (-not $env:PGPASSWORD) {
    Write-Error "Database password is not set. Please set PGPASSWORD or DEV_DB_PASSWORD."
    exit 1
}
$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
$devDb = "packagedeliveryrouteoptimizer"

Write-Host "=================== USERS in $devDb ==================="
& $psql -U app_user -h localhost -p 5432 -d $devDb -c "SELECT id, username, email, full_name, role, active, created_at FROM users ORDER BY id;"

Write-Host "`n=================== DRIVERS in $devDb ==================="
& $psql -U app_user -h localhost -p 5432 -d $devDb -c "SELECT id, user_id, name, license_number, phone, status, active, created_at FROM drivers ORDER BY id;"

Write-Host "`n=================== VEHICLES in $devDb ==================="
& $psql -U app_user -h localhost -p 5432 -d $devDb -c "SELECT id, registration_number, vehicle_type, capacity, status, active, created_at FROM vehicles ORDER BY id;"

Write-Host "`n=================== LOCATIONS in $devDb ==================="
& $psql -U app_user -h localhost -p 5432 -d $devDb -c "SELECT id, name, address, latitude, longitude, location_type, active, created_at FROM locations ORDER BY id;"

Write-Host "`n=================== PACKAGES in $devDb ==================="
& $psql -U app_user -h localhost -p 5432 -d $devDb -c "SELECT id, tracking_number, sender_name, receiver_name, customer_id, source_location_id, destination_location_id, status, created_at FROM packages ORDER BY id;"

Write-Host "`n=================== DELIVERIES in $devDb ==================="
& $psql -U app_user -h localhost -p 5432 -d $devDb -c "SELECT id, package_id, driver_id, vehicle_id, source_location_id, destination_location_id, status, created_at, completed_at FROM deliveries ORDER BY id;"

Write-Host "`n=================== ROUTES in $devDb ==================="
& $psql -U app_user -h localhost -p 5432 -d $devDb -c "SELECT id, delivery_id, source_location_id, destination_location_id, total_distance, estimated_time, algorithm_used, is_current, created_at FROM routes ORDER BY id;"
