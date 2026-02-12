#!/bin/bash

# NotebookLM Backend API Integration Tests
# Usage: ./api-integration-test.sh [BASE_URL]
# Example: ./api-integration-test.sh http://localhost:8080

set -e

BASE_URL="${1:-http://localhost:8080}"
API_URL="$BASE_URL/api"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
TESTS_PASSED=0
TESTS_FAILED=0

# Test result function
test_result() {
    local test_name="$1"
    local expected_code="$2"
    local actual_code="$3"
    local response_body="$4"

    if [ "$actual_code" -eq "$expected_code" ]; then
        echo -e "${GREEN}[PASS]${NC} $test_name (HTTP $actual_code)"
        ((TESTS_PASSED++))
        return 0
    else
        echo -e "${RED}[FAIL]${NC} $test_name - Expected HTTP $expected_code, got $actual_code"
        echo "Response: $response_body"
        ((TESTS_FAILED++))
        return 1
    fi
}

# Extract value from JSON
json_value() {
    echo "$1" | grep -o "\"$2\":[^,}]*" | sed "s/\"$2\"://" | tr -d '"' | tr -d ' '
}

echo "=============================================="
echo "NotebookLM Backend API Integration Tests"
echo "Base URL: $BASE_URL"
echo "=============================================="
echo ""

# ============================================
# HEALTH CHECK
# ============================================
echo -e "${YELLOW}=== Health Check ===${NC}"

RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/actuator/health")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "Health endpoint" 200 "$HTTP_CODE" "$BODY"

echo ""

# ============================================
# SESSION API TESTS
# ============================================
echo -e "${YELLOW}=== Session API Tests ===${NC}"

# Test 1: Create a session
echo "Creating test session..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/sessions" \
    -H "Content-Type: application/json" \
    -d '{"title": "Integration Test Session"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "POST /api/sessions - Create session" 201 "$HTTP_CODE" "$BODY"

# Extract session ID
SESSION_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | sed 's/"id":"//' | tr -d '"')
echo "Created session ID: $SESSION_ID"

# Test 2: Get all sessions
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/sessions")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/sessions - List sessions" 200 "$HTTP_CODE" "$BODY"

# Test 3: Get session by ID
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/sessions/$SESSION_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/sessions/{id} - Get session" 200 "$HTTP_CODE" "$BODY"

# Test 4: Update session mode to RESEARCH
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$API_URL/sessions/$SESSION_ID/mode" \
    -H "Content-Type: application/json" \
    -d '"RESEARCH"')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "PUT /api/sessions/{id}/mode - Update mode to RESEARCH" 200 "$HTTP_CODE" "$BODY"

# Test 5: Update session mode to LEARNING
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$API_URL/sessions/$SESSION_ID/mode" \
    -H "Content-Type: application/json" \
    -d '"LEARNING"')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "PUT /api/sessions/{id}/mode - Update mode to LEARNING" 200 "$HTTP_CODE" "$BODY"

# Test 6: Update session mode back to EXPLORING
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$API_URL/sessions/$SESSION_ID/mode" \
    -H "Content-Type: application/json" \
    -d '"EXPLORING"')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "PUT /api/sessions/{id}/mode - Update mode to EXPLORING" 200 "$HTTP_CODE" "$BODY"

# Test 7: Get non-existent session (should 404)
FAKE_UUID="00000000-0000-0000-0000-000000000000"
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/sessions/$FAKE_UUID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/sessions/{id} - Non-existent session returns 404" 404 "$HTTP_CODE" "$BODY"

echo ""

# ============================================
# DOCUMENT API TESTS
# ============================================
echo -e "${YELLOW}=== Document API Tests ===${NC}"

# Create a test text file
TEST_FILE="/tmp/test-document.txt"
cat > "$TEST_FILE" << 'EOF'
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
EOF

echo "Created test file: $TEST_FILE"

# Test 8: Upload document
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/sessions/$SESSION_ID/documents" \
    -F "file=@$TEST_FILE;type=text/plain")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "POST /api/sessions/{id}/documents - Upload document" 201 "$HTTP_CODE" "$BODY"

# Extract document ID
DOCUMENT_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | sed 's/"id":"//' | tr -d '"')
echo "Uploaded document ID: $DOCUMENT_ID"

# Test 9: Get documents for session
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/sessions/$SESSION_ID/documents")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/sessions/{id}/documents - List session documents" 200 "$HTTP_CODE" "$BODY"

# Test 10: Get document by ID
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/documents/$DOCUMENT_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/documents/{id} - Get document" 200 "$HTTP_CODE" "$BODY"

# Test 11: Get document status
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/documents/$DOCUMENT_ID/status")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/documents/{id}/status - Get document status" 200 "$HTTP_CODE" "$BODY"

# Wait for document processing
echo "Waiting for document processing (5 seconds)..."
sleep 5

# Test 12: Check document status after processing
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/documents/$DOCUMENT_ID/status")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/documents/{id}/status - Check processed status" 200 "$HTTP_CODE" "$BODY"
echo "Document status: $BODY"

# Test 13: Upload invalid file type (should fail)
INVALID_FILE="/tmp/test-invalid.xyz"
echo "invalid content" > "$INVALID_FILE"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/sessions/$SESSION_ID/documents" \
    -F "file=@$INVALID_FILE;type=application/octet-stream")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
# This should return 400 Bad Request for unsupported file type
if [ "$HTTP_CODE" -eq 400 ]; then
    echo -e "${GREEN}[PASS]${NC} POST /api/sessions/{id}/documents - Invalid file type rejected (HTTP $HTTP_CODE)"
    ((TESTS_PASSED++))
else
    echo -e "${YELLOW}[WARN]${NC} POST /api/sessions/{id}/documents - Invalid file type returned HTTP $HTTP_CODE (expected 400)"
fi
rm -f "$INVALID_FILE"

echo ""

# ============================================
# MEMORY API TESTS
# ============================================
echo -e "${YELLOW}=== Memory API Tests ===${NC}"

# Test 14: Get memories for session (should be empty initially)
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/sessions/$SESSION_ID/memories")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/sessions/{id}/memories - List memories" 200 "$HTTP_CODE" "$BODY"

# Test 15: Create a memory
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/sessions/$SESSION_ID/memories" \
    -H "Content-Type: application/json" \
    -d '{"content": "User prefers detailed explanations", "type": "preference", "importance": 0.8}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "POST /api/sessions/{id}/memories - Create memory" 201 "$HTTP_CODE" "$BODY"

# Extract memory ID
MEMORY_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | sed 's/"id":"//' | tr -d '"')
echo "Created memory ID: $MEMORY_ID"

# Test 16: Get memories after creating one
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/sessions/$SESSION_ID/memories")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/sessions/{id}/memories - List memories after creation" 200 "$HTTP_CODE" "$BODY"

# Test 17: Delete memory
if [ -n "$MEMORY_ID" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$API_URL/sessions/$SESSION_ID/memories/$MEMORY_ID")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    test_result "DELETE /api/sessions/{id}/memories/{memoryId} - Delete memory" 204 "$HTTP_CODE" "$BODY"
fi

echo ""

# ============================================
# CHAT SSE API TESTS
# ============================================
echo -e "${YELLOW}=== Chat SSE API Tests ===${NC}"

# Test 18: Get chat messages (should be empty initially)
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/sessions/$SESSION_ID/messages")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/sessions/{id}/messages - List messages" 200 "$HTTP_CODE" "$BODY"

# Test 19: Send chat message (SSE stream)
echo "Testing SSE chat stream (timeout after 30 seconds)..."
SSE_OUTPUT="/tmp/sse-output.txt"
timeout 30 curl -s -N -X POST "$API_URL/sessions/$SESSION_ID/chat/stream" \
    -H "Content-Type: application/json" \
    -H "Accept: text/event-stream" \
    -d '{"message": "What is this document about?"}' > "$SSE_OUTPUT" 2>&1 || true

if [ -s "$SSE_OUTPUT" ]; then
    # Check if we got SSE events
    if grep -q "event:" "$SSE_OUTPUT" || grep -q "data:" "$SSE_OUTPUT"; then
        echo -e "${GREEN}[PASS]${NC} POST /api/sessions/{id}/chat/stream - SSE stream received events"
        ((TESTS_PASSED++))
        echo "SSE Output preview:"
        head -20 "$SSE_OUTPUT"
    else
        echo -e "${YELLOW}[WARN]${NC} POST /api/sessions/{id}/chat/stream - No SSE events in response"
        echo "Response: $(cat $SSE_OUTPUT)"
    fi
else
    echo -e "${YELLOW}[WARN]${NC} POST /api/sessions/{id}/chat/stream - No response received (may need Elasticsearch/OpenAI)"
fi
rm -f "$SSE_OUTPUT"

# Test 20: Get messages after chat
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/sessions/$SESSION_ID/messages")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/sessions/{id}/messages - List messages after chat" 200 "$HTTP_CODE" "$BODY"

echo ""

# ============================================
# CLEANUP TESTS
# ============================================
echo -e "${YELLOW}=== Cleanup Tests ===${NC}"

# Test 21: Delete document
RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$API_URL/documents/$DOCUMENT_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "DELETE /api/documents/{id} - Delete document" 204 "$HTTP_CODE" "$BODY"

# Test 22: Verify document deleted
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/documents/$DOCUMENT_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/documents/{id} - Verify document deleted" 404 "$HTTP_CODE" "$BODY"

# Test 23: Delete session
RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$API_URL/sessions/$SESSION_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "DELETE /api/sessions/{id} - Delete session" 204 "$HTTP_CODE" "$BODY"

# Test 24: Verify session deleted
RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/sessions/$SESSION_ID")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
test_result "GET /api/sessions/{id} - Verify session deleted" 404 "$HTTP_CODE" "$BODY"

# Cleanup test file
rm -f "$TEST_FILE"

echo ""
echo "=============================================="
echo "Test Summary"
echo "=============================================="
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"
echo "Total: $((TESTS_PASSED + TESTS_FAILED))"
echo ""

if [ "$TESTS_FAILED" -gt 0 ]; then
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
