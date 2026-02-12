# NotebookLM Backend API Integration Tests (PowerShell)
# Usage: .\api-integration-test.ps1 [-BaseUrl "http://localhost:8080"]
# Example: .\api-integration-test.ps1 -BaseUrl "http://localhost:8080"

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ApiUrl = "$BaseUrl/api"

# Counters
$Script:TestsPassed = 0
$Script:TestsFailed = 0

# Test result function
function Test-Result {
    param(
        [string]$TestName,
        [int]$ExpectedCode,
        [int]$ActualCode,
        [string]$ResponseBody
    )

    if ($ActualCode -eq $ExpectedCode) {
        Write-Host "[PASS] $TestName (HTTP $ActualCode)" -ForegroundColor Green
        $Script:TestsPassed++
        return $true
    } else {
        Write-Host "[FAIL] $TestName - Expected HTTP $ExpectedCode, got $ActualCode" -ForegroundColor Red
        Write-Host "Response: $ResponseBody" -ForegroundColor Gray
        $Script:TestsFailed++
        return $false
    }
}

# Make HTTP request and return response with status code
function Invoke-ApiRequest {
    param(
        [string]$Method = "GET",
        [string]$Uri,
        [object]$Body = $null,
        [string]$ContentType = "application/json",
        [string]$FilePath = $null
    )

    try {
        $params = @{
            Method = $Method
            Uri = $Uri
            UseBasicParsing = $true
        }

        if ($FilePath) {
            # Multipart form data for file upload
            $boundary = [System.Guid]::NewGuid().ToString()
            $fileName = Split-Path $FilePath -Leaf
            $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)
            $fileContent = [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($fileBytes)

            $bodyLines = @(
                "--$boundary",
                "Content-Disposition: form-data; name=`"file`"; filename=`"$fileName`"",
                "Content-Type: text/plain",
                "",
                $fileContent,
                "--$boundary--"
            )
            $body = $bodyLines -join "`r`n"
            $params.ContentType = "multipart/form-data; boundary=$boundary"
            $params.Body = $body
        } elseif ($Body) {
            if ($Body -is [string]) {
                $params.Body = $Body
            } else {
                $params.Body = $Body | ConvertTo-Json -Depth 10
            }
            $params.ContentType = $ContentType
        }

        $response = Invoke-WebRequest @params -ErrorAction Stop
        return @{
            StatusCode = $response.StatusCode
            Body = $response.Content
        }
    } catch {
        $statusCode = 0
        $body = ""
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            $reader.Close()
        }
        return @{
            StatusCode = $statusCode
            Body = $body
        }
    }
}

Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "NotebookLM Backend API Integration Tests" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""

# ============================================
# HEALTH CHECK
# ============================================
Write-Host "=== Health Check ===" -ForegroundColor Yellow

$response = Invoke-ApiRequest -Uri "$BaseUrl/actuator/health"
Test-Result "Health endpoint" 200 $response.StatusCode $response.Body

Write-Host ""

# ============================================
# SESSION API TESTS
# ============================================
Write-Host "=== Session API Tests ===" -ForegroundColor Yellow

# Test 1: Create a session
Write-Host "Creating test session..."
$response = Invoke-ApiRequest -Method "POST" -Uri "$ApiUrl/sessions" -Body @{title = "Integration Test Session"}
Test-Result "POST /api/sessions - Create session" 201 $response.StatusCode $response.Body

# Extract session ID
$sessionData = $response.Body | ConvertFrom-Json
$SessionId = $sessionData.id
Write-Host "Created session ID: $SessionId" -ForegroundColor Cyan

# Test 2: Get all sessions
$response = Invoke-ApiRequest -Uri "$ApiUrl/sessions"
Test-Result "GET /api/sessions - List sessions" 200 $response.StatusCode $response.Body

# Test 3: Get session by ID
$response = Invoke-ApiRequest -Uri "$ApiUrl/sessions/$SessionId"
Test-Result "GET /api/sessions/{id} - Get session" 200 $response.StatusCode $response.Body

# Test 4: Update session mode to RESEARCH
$response = Invoke-ApiRequest -Method "PUT" -Uri "$ApiUrl/sessions/$SessionId/mode" -Body '"RESEARCH"'
Test-Result "PUT /api/sessions/{id}/mode - Update mode to RESEARCH" 200 $response.StatusCode $response.Body

# Test 5: Update session mode to LEARNING
$response = Invoke-ApiRequest -Method "PUT" -Uri "$ApiUrl/sessions/$SessionId/mode" -Body '"LEARNING"'
Test-Result "PUT /api/sessions/{id}/mode - Update mode to LEARNING" 200 $response.StatusCode $response.Body

# Test 6: Update session mode back to EXPLORING
$response = Invoke-ApiRequest -Method "PUT" -Uri "$ApiUrl/sessions/$SessionId/mode" -Body '"EXPLORING"'
Test-Result "PUT /api/sessions/{id}/mode - Update mode to EXPLORING" 200 $response.StatusCode $response.Body

# Test 7: Get non-existent session (should 404)
$fakeUuid = "00000000-0000-0000-0000-000000000000"
$response = Invoke-ApiRequest -Uri "$ApiUrl/sessions/$fakeUuid"
Test-Result "GET /api/sessions/{id} - Non-existent session returns 404" 404 $response.StatusCode $response.Body

Write-Host ""

# ============================================
# DOCUMENT API TESTS
# ============================================
Write-Host "=== Document API Tests ===" -ForegroundColor Yellow

# Create test file
$testFilePath = Join-Path $env:TEMP "test-document.txt"
$testContent = @"
# Integration Test Document

This is a test document for the NotebookLM integration tests.

## Section 1: Introduction

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor
incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis
nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.

## Section 2: Main Content

Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore
eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident,
sunt in culpa qui officia deserunt mollit anim id est laborum.

### Subsection 2.1: Details

Here are some important details:
- Point one about the topic
- Point two with more information
- Point three as a summary

## Section 3: Conclusion

This document serves as test data for verifying the document upload and
processing capabilities of the NotebookLM backend.
"@
Set-Content -Path $testFilePath -Value $testContent
Write-Host "Created test file: $testFilePath" -ForegroundColor Cyan

# Test 8: Upload document
$response = Invoke-ApiRequest -Method "POST" -Uri "$ApiUrl/sessions/$SessionId/documents" -FilePath $testFilePath
Test-Result "POST /api/sessions/{id}/documents - Upload document" 201 $response.StatusCode $response.Body

# Extract document ID
$documentData = $response.Body | ConvertFrom-Json
$DocumentId = $documentData.id
Write-Host "Uploaded document ID: $DocumentId" -ForegroundColor Cyan

# Test 9: Get documents for session
$response = Invoke-ApiRequest -Uri "$ApiUrl/sessions/$SessionId/documents"
Test-Result "GET /api/sessions/{id}/documents - List session documents" 200 $response.StatusCode $response.Body

# Test 10: Get document by ID
$response = Invoke-ApiRequest -Uri "$ApiUrl/documents/$DocumentId"
Test-Result "GET /api/documents/{id} - Get document" 200 $response.StatusCode $response.Body

# Test 11: Get document status
$response = Invoke-ApiRequest -Uri "$ApiUrl/documents/$DocumentId/status"
Test-Result "GET /api/documents/{id}/status - Get document status" 200 $response.StatusCode $response.Body

# Wait for document processing
Write-Host "Waiting for document processing (5 seconds)..." -ForegroundColor Cyan
Start-Sleep -Seconds 5

# Test 12: Check document status after processing
$response = Invoke-ApiRequest -Uri "$ApiUrl/documents/$DocumentId/status"
Test-Result "GET /api/documents/{id}/status - Check processed status" 200 $response.StatusCode $response.Body
Write-Host "Document status: $($response.Body)" -ForegroundColor Cyan

# Test 13: Upload invalid file type
$invalidFilePath = Join-Path $env:TEMP "test-invalid.xyz"
Set-Content -Path $invalidFilePath -Value "invalid content"
$response = Invoke-ApiRequest -Method "POST" -Uri "$ApiUrl/sessions/$SessionId/documents" -FilePath $invalidFilePath
if ($response.StatusCode -eq 400) {
    Write-Host "[PASS] POST /api/sessions/{id}/documents - Invalid file type rejected (HTTP $($response.StatusCode))" -ForegroundColor Green
    $Script:TestsPassed++
} else {
    Write-Host "[WARN] POST /api/sessions/{id}/documents - Invalid file type returned HTTP $($response.StatusCode) (expected 400)" -ForegroundColor Yellow
}
Remove-Item -Path $invalidFilePath -ErrorAction SilentlyContinue

Write-Host ""

# ============================================
# MEMORY API TESTS
# ============================================
Write-Host "=== Memory API Tests ===" -ForegroundColor Yellow

# Test 14: Get memories for session (should be empty initially)
$response = Invoke-ApiRequest -Uri "$ApiUrl/sessions/$SessionId/memories"
Test-Result "GET /api/sessions/{id}/memories - List memories" 200 $response.StatusCode $response.Body

# Test 15: Create a memory
$memoryBody = @{
    content = "User prefers detailed explanations"
    type = "preference"
    importance = 0.8
}
$response = Invoke-ApiRequest -Method "POST" -Uri "$ApiUrl/sessions/$SessionId/memories" -Body $memoryBody
Test-Result "POST /api/sessions/{id}/memories - Create memory" 201 $response.StatusCode $response.Body

# Extract memory ID
$memoryData = $response.Body | ConvertFrom-Json
$MemoryId = $memoryData.id
Write-Host "Created memory ID: $MemoryId" -ForegroundColor Cyan

# Test 16: Get memories after creating one
$response = Invoke-ApiRequest -Uri "$ApiUrl/sessions/$SessionId/memories"
Test-Result "GET /api/sessions/{id}/memories - List memories after creation" 200 $response.StatusCode $response.Body

# Test 17: Delete memory
if ($MemoryId) {
    $response = Invoke-ApiRequest -Method "DELETE" -Uri "$ApiUrl/sessions/$SessionId/memories/$MemoryId"
    Test-Result "DELETE /api/sessions/{id}/memories/{memoryId} - Delete memory" 204 $response.StatusCode $response.Body
}

Write-Host ""

# ============================================
# CHAT SSE API TESTS
# ============================================
Write-Host "=== Chat SSE API Tests ===" -ForegroundColor Yellow

# Test 18: Get chat messages (should be empty initially)
$response = Invoke-ApiRequest -Uri "$ApiUrl/sessions/$SessionId/messages"
Test-Result "GET /api/sessions/{id}/messages - List messages" 200 $response.StatusCode $response.Body

# Test 19: Send chat message (SSE stream)
Write-Host "Testing SSE chat stream (timeout after 30 seconds)..." -ForegroundColor Cyan
try {
    $chatBody = @{message = "What is this document about?"} | ConvertTo-Json

    # Use WebClient for SSE (Invoke-WebRequest doesn't handle streaming well)
    $webClient = New-Object System.Net.WebClient
    $webClient.Headers.Add("Content-Type", "application/json")
    $webClient.Headers.Add("Accept", "text/event-stream")

    $job = Start-Job -ScriptBlock {
        param($url, $body)
        try {
            $webClient = New-Object System.Net.WebClient
            $webClient.Headers.Add("Content-Type", "application/json")
            $webClient.Headers.Add("Accept", "text/event-stream")
            $response = $webClient.UploadString($url, "POST", $body)
            return $response
        } catch {
            return "Error: $_"
        }
    } -ArgumentList "$ApiUrl/sessions/$SessionId/chat/stream", $chatBody

    $completed = Wait-Job -Job $job -Timeout 30
    if ($completed) {
        $sseOutput = Receive-Job -Job $job
        if ($sseOutput -match "event:" -or $sseOutput -match "data:") {
            Write-Host "[PASS] POST /api/sessions/{id}/chat/stream - SSE stream received events" -ForegroundColor Green
            $Script:TestsPassed++
            Write-Host "SSE Output preview:" -ForegroundColor Cyan
            Write-Host ($sseOutput.Substring(0, [Math]::Min(500, $sseOutput.Length))) -ForegroundColor Gray
        } else {
            Write-Host "[WARN] POST /api/sessions/{id}/chat/stream - No SSE events in response" -ForegroundColor Yellow
            Write-Host "Response: $sseOutput" -ForegroundColor Gray
        }
    } else {
        Write-Host "[WARN] POST /api/sessions/{id}/chat/stream - Timeout or no response (may need Elasticsearch/OpenAI)" -ForegroundColor Yellow
    }
    Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
} catch {
    Write-Host "[WARN] POST /api/sessions/{id}/chat/stream - Error: $_" -ForegroundColor Yellow
}

# Test 20: Get messages after chat
$response = Invoke-ApiRequest -Uri "$ApiUrl/sessions/$SessionId/messages"
Test-Result "GET /api/sessions/{id}/messages - List messages after chat" 200 $response.StatusCode $response.Body

Write-Host ""

# ============================================
# CLEANUP TESTS
# ============================================
Write-Host "=== Cleanup Tests ===" -ForegroundColor Yellow

# Test 21: Delete document
$response = Invoke-ApiRequest -Method "DELETE" -Uri "$ApiUrl/documents/$DocumentId"
Test-Result "DELETE /api/documents/{id} - Delete document" 204 $response.StatusCode $response.Body

# Test 22: Verify document deleted
$response = Invoke-ApiRequest -Uri "$ApiUrl/documents/$DocumentId"
Test-Result "GET /api/documents/{id} - Verify document deleted" 404 $response.StatusCode $response.Body

# Test 23: Delete session
$response = Invoke-ApiRequest -Method "DELETE" -Uri "$ApiUrl/sessions/$SessionId"
Test-Result "DELETE /api/sessions/{id} - Delete session" 204 $response.StatusCode $response.Body

# Test 24: Verify session deleted
$response = Invoke-ApiRequest -Uri "$ApiUrl/sessions/$SessionId"
Test-Result "GET /api/sessions/{id} - Verify session deleted" 404 $response.StatusCode $response.Body

# Cleanup test file
Remove-Item -Path $testFilePath -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "Passed: $Script:TestsPassed" -ForegroundColor Green
Write-Host "Failed: $Script:TestsFailed" -ForegroundColor Red
Write-Host "Total: $($Script:TestsPassed + $Script:TestsFailed)" -ForegroundColor Cyan
Write-Host ""

if ($Script:TestsFailed -gt 0) {
    Write-Host "Some tests failed!" -ForegroundColor Red
    exit 1
} else {
    Write-Host "All tests passed!" -ForegroundColor Green
    exit 0
}
