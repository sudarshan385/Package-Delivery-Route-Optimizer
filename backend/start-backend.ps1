# ==============================================================================
# Script: backend/start-backend.ps1
# Description: Canonical startup script for RouteOptimizer Spring Boot Backend
# Profile: dev (Target DB: packagedeliveryrouteoptimizer)
# ==============================================================================

# 1. Java 21 Runtime Environment Detection & Selection
Write-Host "Verifying Java 21 runtime..."
$javaExe = $null

if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    $output = cmd /c """$candidate"" -version 2>&1"
    if ($output -match '"21\.') {
        $javaExe = $candidate
    }
}

if (-not $javaExe) {
    # Check standard JDK 21 installation locations
    $knownJdk21Paths = @(
        "C:\Program Files\Java\jdk-21.0.12.1",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\BellSoft\LibericaJDK-21*"
    )
    foreach ($pattern in $knownJdk21Paths) {
        $found = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found -and (Test-Path (Join-Path $found.FullName "bin\java.exe"))) {
            $candidate = Join-Path $found.FullName "bin\java.exe"
            $testOutput = cmd /c """$candidate"" -version 2>&1"
            if ($testOutput -match '"21\.') {
                $env:JAVA_HOME = $found.FullName
                $javaExe = $candidate
                break
            }
        }
    }
}

if (-not $javaExe) {
    # Check default system PATH java
    $sysJava = Get-Command java -ErrorAction SilentlyContinue
    if ($sysJava) {
        $testOutput = cmd /c "java -version 2>&1"
        if ($testOutput -match '"21\.') {
            $javaExe = $sysJava.Source
        }
    }
}

if (-not $javaExe) {
    Write-Error "[FATAL] Java 21 is required to start RouteOptimizer backend, but no Java 21 installation was found. Please set JAVA_HOME to JDK 21."
    exit 1
}

# Prepend JAVA_HOME\bin to PATH for this process session
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "[OK] Java 21 configured: $env:JAVA_HOME"

# Ensure Maven is available on PATH
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $userPath = [System.Environment]::GetEnvironmentVariable("PATH", "User")
    if ($userPath) {
        $env:PATH = "$userPath;$env:PATH"
    }
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $knownMavenPaths = @(
        "$env:USERPROFILE\Downloads\apache-maven-*\bin",
        "C:\Program Files\apache-maven-*\bin",
        "C:\apache-maven-*\bin"
    )
    foreach ($mPattern in $knownMavenPaths) {
        $foundM = Get-Item $mPattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($foundM -and (Test-Path (Join-Path $foundM.FullName "mvn.cmd"))) {
            $env:PATH = "$($foundM.FullName);$env:PATH"
            break
        }
    }
}
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "[OK] Maven configured: $((Get-Command mvn).Source)"
} else {
    Write-Error "[FATAL] Maven (mvn) not found. Please install Maven or add it to PATH."
    exit 1
}

# 2. Environment Configuration & Validation (Fail-Early)
if (-not $env:DEV_DB_URL) {
    $env:DEV_DB_URL = "jdbc:postgresql://localhost:5432/packagedeliveryrouteoptimizer"
}
if (-not $env:DEV_DB_USERNAME) {
    $env:DEV_DB_USERNAME = "app_user"
}
if (-not $env:DEV_DB_PASSWORD) {
    $env:DEV_DB_PASSWORD = "app_password"
}
if (-not $env:JWT_SECRET) {
    $env:JWT_SECRET = "dev-suite-dedicated-hmac-sha256-signing-key-min-32-bytes"
}
if (-not $env:SPRING_PROFILES_ACTIVE) {
    $env:SPRING_PROFILES_ACTIVE = "dev"
}

# Strict validation of JWT_SECRET
$forbiddenHex = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
$placeholders = @('${JWT_SECRET}', 'CHANGE_ME', 'YOUR_SECRET_KEY', 'SECRET', 'SECRET_KEY', 'DEFAULT_SECRET', 'MY_SECRET_KEY')
$secretTrimmed = $env:JWT_SECRET.Trim()

if ([System.Text.Encoding]::UTF8.GetByteCount($secretTrimmed) -lt 32) {
    Write-Error "[FATAL] JWT_SECRET configuration error: HS256 requires at least 32 bytes (256 bits) of key material."
    exit 1
}
if ($secretTrimmed -ieq $forbiddenHex -or $placeholders -contains $secretTrimmed.ToUpper()) {
    Write-Error "[FATAL] JWT_SECRET configuration error: Forbidden default or placeholder signing key detected."
    exit 1
}

# 3. Pre-flight PostgreSQL Connectivity & Credential Verification
$psqlPaths = @(
    "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    "C:\Program Files\PostgreSQL\17\bin\psql.exe",
    "C:\Program Files\PostgreSQL\16\bin\psql.exe"
)
$psqlExe = $null
foreach ($p in $psqlPaths) {
    if (Test-Path $p) {
        $psqlExe = $p
        break
    }
}
if (-not $psqlExe) {
    $foundPsql = Get-Command psql -ErrorAction SilentlyContinue
    if ($foundPsql) { $psqlExe = $foundPsql.Source }
}

if ($psqlExe) {
    Write-Host "Verifying PostgreSQL connection for user '$($env:DEV_DB_USERNAME)' against database 'packagedeliveryrouteoptimizer'..."
    $env:PGPASSWORD = $env:DEV_DB_PASSWORD
    try {
        $checkDbResult = cmd /c """$psqlExe"" -U $($env:DEV_DB_USERNAME) -h localhost -p 5432 -d packagedeliveryrouteoptimizer -t -A -c ""SELECT current_database() || '|' || current_user;"" 2>&1"
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[FATAL] Preflight PostgreSQL authentication failed for user '$($env:DEV_DB_USERNAME)' against database 'packagedeliveryrouteoptimizer'. Verify PostgreSQL service is running and DEV_DB_PASSWORD is correct."
            exit 1
        }
        $rawOutput = ($checkDbResult | Out-String).Trim()
        $parts = $rawOutput.Split('|')
        if ($parts.Length -lt 2 -or $parts[0].Trim() -ne "packagedeliveryrouteoptimizer" -or $parts[1].Trim() -ne $env:DEV_DB_USERNAME) {
            Write-Error "[FATAL] Preflight database identity mismatch! Expected packagedeliveryrouteoptimizer|$($env:DEV_DB_USERNAME) but got $rawOutput"
            exit 1
        }
        Write-Host "[OK] Preflight PostgreSQL verified: Connected to $($parts[0].Trim()) as $($parts[1].Trim())"
    } finally {
        $env:PGPASSWORD = $null
    }
} else {
    Write-Warning "psql.exe not found on standard paths; skipping external CLI preflight check."
}

# 4. Port 8080 & Instance Guard
$port = 8080
$tcpConns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

if ($tcpConns) {
    $pidOnPort = $tcpConns[0].OwningProcess
    $process = Get-Process -Id $pidOnPort -ErrorAction SilentlyContinue

    if ($process) {
        if ($process.Name -match "java") {
            Write-Host "RouteOptimizer backend is already running on http://localhost:8080 (PID: $pidOnPort)."
            exit 0
        } else {
            Write-Error "Port $port is in use by another application ($($process.Name), PID: $pidOnPort). Please stop that application and try again."
            exit 1
        }
    }
}

# 5. Launch Backend with Active Development Profile
Write-Host "Starting RouteOptimizer backend on port 8080 with profile 'dev'..."
Set-Location $PSScriptRoot
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
exit $LASTEXITCODE
