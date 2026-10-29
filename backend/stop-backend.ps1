$port = 8080
$tcpConns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

if ($tcpConns) {
    $pidOnPort = $tcpConns[0].OwningProcess
    $process = Get-Process -Id $pidOnPort -ErrorAction SilentlyContinue

    if ($process) {
        if ($process.Name -match "java") {
            Write-Host "Stopping RouteOptimizer backend (PID: $pidOnPort)..."
            Stop-Process -Id $pidOnPort -Force
            Write-Host "Backend stopped successfully."
            exit 0
        } else {
            Write-Host "Process on port $port is not RouteOptimizer ($($process.Name)). Refusing to kill."
            exit 1
        }
    }
}

Write-Host "RouteOptimizer backend is not running."
exit 0
