# NotebookLM API Integration Tests

Comprehensive integration test scripts for testing all backend API endpoints.

## Prerequisites

1. **Backend running** at `http://localhost:8080`
   ```bash
   cd backend
   ./gradlew bootRun
   ```

2. **Elasticsearch running** (required for document processing and chat)
   ```bash
   docker compose up -d
   ```

3. **OpenAI API key** configured in environment
   ```bash
   export OPENAI_API_KEY="your-openai-api-key"
   ```

## Test Scripts

### PowerShell (Windows)

```powershell
# Run with default URL (localhost:8080)
.\api-integration-test.ps1

# Run with custom URL
.\api-integration-test.ps1 -BaseUrl "http://localhost:8080"
```

### Bash (Linux/macOS/Git Bash)

```bash
# Make executable
chmod +x api-integration-test.sh

# Run with default URL
./api-integration-test.sh

# Run with custom URL
./api-integration-test.sh http://localhost:8080
```

## Test Coverage

The scripts test **24 scenarios** across all API endpoints:

### Health Check (1 test)
- GET /actuator/health

### Session API (7 tests)
- POST /api/sessions - Create session
- GET /api/sessions - List sessions
- GET /api/sessions/{id} - Get session
- PUT /api/sessions/{id}/mode - Update to RESEARCH
- PUT /api/sessions/{id}/mode - Update to LEARNING
- PUT /api/sessions/{id}/mode - Update to EXPLORING
- GET /api/sessions/{id} - 404 for non-existent

### Document API (6 tests)
- POST /api/sessions/{id}/documents - Upload document
- GET /api/sessions/{id}/documents - List documents
- GET /api/documents/{id} - Get document
- GET /api/documents/{id}/status - Get status
- GET /api/documents/{id}/status - Check after processing
- POST /api/sessions/{id}/documents - Reject invalid file type

### Memory API (4 tests)
- GET /api/sessions/{id}/memories - List memories (empty)
- POST /api/sessions/{id}/memories - Create memory
- GET /api/sessions/{id}/memories - List after creation
- DELETE /api/sessions/{id}/memories/{id} - Delete memory

### Chat SSE API (3 tests)
- GET /api/sessions/{id}/messages - List messages (empty)
- POST /api/sessions/{id}/chat/stream - SSE chat stream
- GET /api/sessions/{id}/messages - List after chat

### Cleanup (4 tests)
- DELETE /api/documents/{id} - Delete document
- GET /api/documents/{id} - Verify deleted (404)
- DELETE /api/sessions/{id} - Delete session
- GET /api/sessions/{id} - Verify deleted (404)

## Test Data

Sample documents are provided in `test-data/`:

- `sample-document.txt` - Machine learning guide (for general testing)
- `technical-reference.txt` - Spring Boot reference (for technical queries)

You can use these for manual testing:

```bash
# Upload a test document
curl -X POST http://localhost:8080/api/sessions/{session-id}/documents \
  -F "file=@test-data/sample-document.txt;type=text/plain"
```

## Expected Output

Successful run:
```
==============================================
NotebookLM Backend API Integration Tests
Base URL: http://localhost:8080
==============================================

=== Health Check ===
[PASS] Health endpoint (HTTP 200)

=== Session API Tests ===
Creating test session...
[PASS] POST /api/sessions - Create session (HTTP 201)
Created session ID: abc123-...
...

==============================================
Test Summary
==============================================
Passed: 24
Failed: 0
Total: 24

All tests passed!
```

## Troubleshooting

### Connection Refused
- Ensure backend is running: `./gradlew bootRun`

### Document Processing Fails
- Ensure Elasticsearch is running: `docker compose up -d`
- Check Elasticsearch logs: `docker compose logs elasticsearch`

### Chat/SSE Times Out
- Ensure OpenAI API key is set
- Check backend logs for API errors

### Test Failures
- Check response body in failure output
- Verify API endpoint URLs match your backend version
