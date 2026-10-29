$adminUser = if ($env:ADMIN_USERNAME) { $env:ADMIN_USERNAME } else { "sudarshan" }
$adminPass = $env:ADMIN_PASSWORD
if (-not $adminPass) {
    Write-Error "ADMIN_PASSWORD environment variable is required to run diagnostic tests."
    exit 1
}

$loginBody = @{ username = $adminUser; password = $adminPass } | ConvertTo-Json
$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -Body $loginBody -ContentType "application/json"
$token = $loginResp.accessToken
Write-Host "Diagnostic authentication successful for: $($loginResp.username) ($($loginResp.role))"

$headers = @{ Authorization = "Bearer $token" }

Write-Host "`nTesting OSRM endpoint for Edge 7 (19 -> 21):"
$edge7 = Invoke-RestMethod -Uri "http://localhost:8080/api/edges/calculate-route?sourceId=19&destinationId=21" -Method GET -Headers $headers
Write-Host "Distance: $($edge7.distanceKm) km, Duration: $($edge7.durationMin) min, Has Geometry: $([bool]$edge7.geometryGeoJson)"

Write-Host "`nTesting OSRM endpoint for Edge 8 (21 -> 20):"
$edge8 = Invoke-RestMethod -Uri "http://localhost:8080/api/edges/calculate-route?sourceId=21&destinationId=20" -Method GET -Headers $headers
Write-Host "Distance: $($edge8.distanceKm) km, Duration: $($edge8.durationMin) min, Has Geometry: $([bool]$edge8.geometryGeoJson)"
