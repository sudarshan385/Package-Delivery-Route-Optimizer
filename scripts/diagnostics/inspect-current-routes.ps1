$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
if (-not $env:PGPASSWORD -and $env:DEV_DB_PASSWORD) { $env:PGPASSWORD = $env:DEV_DB_PASSWORD }
if (-not $env:PGPASSWORD) {
    Write-Error "Database password is not set. Please set PGPASSWORD or DEV_DB_PASSWORD."
    exit 1
}
Write-Host "=== CURRENT ROUTES ==="
& $psql -U app_user -h localhost -p 5432 -d packagedeliveryrouteoptimizer -c "SELECT id, delivery_id, source_location_id, destination_location_id, version_number, is_current, total_distance, estimated_time, algorithm_used FROM routes;"
Write-Host "=== ROUTE STOPS ==="
& $psql -U app_user -h localhost -p 5432 -d packagedeliveryrouteoptimizer -c "SELECT id, route_id, location_id, stop_order, stop_type FROM route_stops ORDER BY route_id, stop_order;"
Write-Host "=== ROUTE EDGES ==="
& $psql -U app_user -h localhost -p 5432 -d packagedeliveryrouteoptimizer -c "SELECT id, route_id, edge_order, source_location_id, destination_location_id, distance, estimated_time, SUBSTRING(geometry_geojson, 1, 30) as geom_preview, (geometry_geojson IS NOT NULL) as has_geom FROM route_edges ORDER BY route_id, edge_order;"
Write-Host "=== LOCATION EDGES ==="
& $psql -U app_user -h localhost -p 5432 -d packagedeliveryrouteoptimizer -c "SELECT id, source_location_id, destination_location_id, distance, estimated_time, SUBSTRING(geometry_geojson, 1, 30) as geom_preview, (geometry_geojson IS NOT NULL) as has_geom FROM location_edges;"
