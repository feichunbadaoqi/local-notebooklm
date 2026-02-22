# Restart Backend
# Stops running processes, rebuilds, and starts the backend with UTF-8 support.

Write-Host "=== Restarting Backend ===" -ForegroundColor Green

# Stop any running Gradle/Java processes
Write-Host "`n1. Stopping running processes..." -ForegroundColor Yellow
Get-Process | Where-Object {$_.ProcessName -like "*java*" -and $_.CommandLine -like "*gradle*"} | Stop-Process -Force -ErrorAction SilentlyContinue

# Clean and rebuild (skip tests for quick restart)
Write-Host "`n2. Building backend..." -ForegroundColor Yellow
./gradlew clean build -x test

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild failed! Check errors above." -ForegroundColor Red
    exit 1
}

# Set UTF-8 encoding for CJK character support
Write-Host "`n3. Setting console to UTF-8..." -ForegroundColor Yellow
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

Write-Host "`n=== Starting Backend ===" -ForegroundColor Green
Write-Host "Starting backend... (Ctrl+C to stop)" -ForegroundColor Yellow
./gradlew bootRun
