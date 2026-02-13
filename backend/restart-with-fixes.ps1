# Restart Backend with All Chinese Support Fixes
# Run this script to apply all the code changes

Write-Host "=== Restarting Backend with Chinese Support Fixes ===" -ForegroundColor Green

# Stop any running Gradle processes
Write-Host "`n1. Stopping any running Gradle processes..." -ForegroundColor Yellow
Get-Process | Where-Object {$_.ProcessName -like "*java*" -and $_.CommandLine -like "*gradle*"} | Stop-Process -Force -ErrorAction SilentlyContinue

# Clean and rebuild (skip tests for quick restart)
Write-Host "`n2. Cleaning and rebuilding backend (skipping tests)..." -ForegroundColor Yellow
./gradlew clean build -x test

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild failed! Check errors above." -ForegroundColor Red
    exit 1
}

# Set UTF-8 encoding
Write-Host "`n3. Setting console to UTF-8..." -ForegroundColor Yellow
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"

Write-Host "`n=== Starting Backend with Fixes ===" -ForegroundColor Green
Write-Host "Token estimation: 1.8 chars/token (was 2.5)" -ForegroundColor Cyan
Write-Host "Max chunk size: 9000 chars (was 15000)" -ForegroundColor Cyan
Write-Host "Keyword extraction: Unicode support (Chinese + English)" -ForegroundColor Cyan
Write-Host "Console encoding: UTF-8" -ForegroundColor Cyan

Write-Host "`nStarting backend... (Ctrl+C to stop)" -ForegroundColor Yellow
./gradlew bootRun
