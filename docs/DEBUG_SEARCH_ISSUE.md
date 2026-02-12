# Debugging RAG Search Issues - Step by Step Guide

## Problem
Uploaded documents are not being retrieved when searching/chatting. We need to trace the full flow from indexing to retrieval.

## What We're Logging Now

### 1. **Document Indexing** (when you upload a file)
```
========== INDEXING DOCUMENT {uuid} ==========
Session ID: 12345678-1234-1234-1234-123456789abc
Document file name: my-document.pdf
Number of valid chunks to index: 15

Sample chunk 0: id=xxx_0, sessionId=12345678..., documentId=abcdef...
===== CHUNK 0 FULL CONTENT START =====
This is the complete text of chunk 0. You'll see every single
character that was extracted from your PDF and will be stored
in Elasticsearch for searching.
===== CHUNK 0 FULL CONTENT END =====

Sample chunk 1: id=xxx_1, sessionId=12345678..., documentId=abcdef...
===== CHUNK 1 FULL CONTENT START =====
This is the complete text of chunk 1...
===== CHUNK 1 FULL CONTENT END =====

[... per chunk indexing details ...]

Indexing chunk xxx_0: sessionId=12345..., documentId=abcdef..., file=my-document.pdf, contentLength=512, embeddingSize=3072
===== FULL CHUNK CONTENT START =====
This is the complete text of chunk 0 being indexed...
===== FULL CHUNK CONTENT END =====

Successfully indexed 15 chunks to Elasticsearch
Elasticsearch indexing complete for document {uuid}
========== INDEXING COMPLETE ==========
```

### 2. **Vector Search** (when you ask a question)
```
========== VECTOR SEARCH START ==========
vectorSearch called for session 12345678-1234-1234-1234-123456789abc, topK=8, embedding size=3072
Filtering by sessionId: 12345678-1234-1234-1234-123456789abc
First 5 dimensions of query embedding: [0.12345, 0.67890, -0.23456, 0.98765, -0.11111]
Executing vector search on index: notebooklm-chunks

Vector search returned 5 results (total hits: 5)

Result 0: sessionId=12345..., documentId=abcdef..., file=my-document.pdf, chunkIndex=0, score=0.987
===== RETRIEVED CHUNK 0 FULL CONTENT START =====
This is the complete text of the chunk that was retrieved from Elasticsearch...
===== RETRIEVED CHUNK 0 FULL CONTENT END =====

Result 1: sessionId=12345..., documentId=abcdef..., file=my-document.pdf, chunkIndex=1, score=0.954
===== RETRIEVED CHUNK 1 FULL CONTENT START =====
...
===== RETRIEVED CHUNK 1 FULL CONTENT END =====

========== VECTOR SEARCH END ==========
```

### 3. **Keyword Search** (BM25 text matching)
```
========== KEYWORD SEARCH START ==========
keywordSearch called for session 12345678-1234-1234-1234-123456789abc, query='artificial intelligence', topK=8
Filtering by sessionId: 12345678-1234-1234-1234-123456789abc
Executing keyword search on index: notebooklm-chunks

Keyword search returned 3 results (total hits: 3)

Result 0: sessionId=12345..., documentId=abcdef..., file=my-document.pdf, chunkIndex=2, score=12.345
===== RETRIEVED CHUNK 0 FULL CONTENT START =====
...artificial intelligence and machine learning are transforming...
===== RETRIEVED CHUNK 0 FULL CONTENT END =====

========== KEYWORD SEARCH END ==========
```

## Testing Steps

### Step 1: Start Backend with Logging
```bash
cd backend

# Make sure Elasticsearch is running
docker compose up -d

# Start backend (logs will show in console)
./gradlew bootRun
```

### Step 2: Upload a Test Document

1. Open http://localhost:4200 in browser
2. Create a new notebook/session
3. **Upload a simple text/PDF file** (start with something small like a 1-page document)
4. **Watch the backend console logs**

### Step 3: Verify Indexing Logs

Look for these sections in the logs:

✅ **Check 1: Document Processing Started**
```
========== INDEXING DOCUMENT {uuid} ==========
Session ID: {your-session-id}
Document file name: {your-file-name}
Number of valid chunks to index: {number}
```

✅ **Check 2: Chunk Content is Readable**
```
===== CHUNK 0 FULL CONTENT START =====
{Should see readable text from your document, not gibberish}
===== CHUNK 0 FULL CONTENT END =====
```

✅ **Check 3: Chunks Indexed Successfully**
```
Successfully indexed {N} chunks to Elasticsearch
========== INDEXING COMPLETE ==========
```

🔴 **Red Flag: If you see any of these:**
- "No content extracted from document" → Tika parsing failed
- "Skipping chunk X due to empty embedding" → OpenAI API issue
- "Too many chunks failed embedding generation" → Document rejected
- "Circuit breaker open for Elasticsearch" → Elasticsearch down

### Step 4: Try to Search/Chat

1. In the chat, ask a question about your document
   - Example: If you uploaded a doc about AI, ask "What is AI?"
2. **Watch the backend console logs**

### Step 5: Verify Search Logs

✅ **Check 4: Vector Search Executed**
```
========== VECTOR SEARCH START ==========
vectorSearch called for session {your-session-id}, topK=8, embedding size=3072
Filtering by sessionId: {your-session-id}
```

✅ **Check 5: Results Returned**
```
Vector search returned 5 results (total hits: 5)
Result 0: sessionId={your-session-id}, documentId={doc-id}, ...
===== RETRIEVED CHUNK 0 FULL CONTENT START =====
{Should see text from your document that matches your query}
===== RETRIEVED CHUNK 0 FULL CONTENT END =====
```

✅ **Check 6: Keyword Search Also Works**
```
========== KEYWORD SEARCH START ==========
Keyword search returned 3 results (total hits: 3)
```

🔴 **Red Flag: If you see:**
- "Vector search returned NO RESULTS for session {id}!" → Problem!
- "Keyword search returned NO RESULTS for session {id} and query '...'!" → Problem!

## Diagnostic Checklist

Use this to identify the exact issue:

| Symptom | Likely Cause | What to Check |
|---------|--------------|---------------|
| ✅ Indexing logs show chunks<br>❌ Search returns no results | **Session ID mismatch** | Compare sessionId in indexing vs search logs. They must match! |
| ✅ Indexing shows "Successfully indexed"<br>❌ Search returns no results | **Elasticsearch not persisting** | Check `docker compose ps` - is Elasticsearch running? Check for errors in ES logs |
| ❌ "No content extracted" during indexing | **Tika parsing failed** | Check document format. Try a simple .txt or .pdf file first |
| ❌ "Skipping chunk due to empty embedding" | **OpenAI API issue** | Check `OPENAI_API_KEY` in application.yaml. Check OpenAI API status |
| ❌ "Circuit breaker open for Elasticsearch" | **Elasticsearch down** | Run `docker compose up -d` and wait 30 seconds |
| ✅ Vector search works<br>❌ Keyword search fails | **BM25 index issue** | Check Elasticsearch index mapping. May need to recreate index |
| ✅ Search works in backend logs<br>❌ UI shows no response | **Frontend-backend disconnect** | Check browser console. Check if SSE connection is established |

## What to Share for Debugging

If the issue persists, share these log sections:

### 1. **Indexing Section** (copy/paste from logs)
```
========== INDEXING DOCUMENT ... ==========
... (include everything until "INDEXING COMPLETE")
```

### 2. **Search Sections** (copy/paste from logs)
```
========== VECTOR SEARCH START ==========
... (include everything until "VECTOR SEARCH END")

========== KEYWORD SEARCH START ==========
... (include everything until "KEYWORD SEARCH END")
```

### 3. **Key Information**
- Session ID from indexing: `...`
- Session ID from search: `...`
- Document file name: `...`
- Number of chunks indexed: `...`
- Number of results from vector search: `...`
- Number of results from keyword search: `...`

## Common Fixes

### Fix 1: Session ID Mismatch
**Symptom:** sessionId in indexing ≠ sessionId in search

**Cause:** Frontend might be creating a new session or not passing correct session ID

**Fix:** Check browser network tab - are requests using the correct session ID?

### Fix 2: Elasticsearch Not Running
**Symptom:** "Circuit breaker open", connection errors

**Fix:**
```bash
cd backend
docker compose up -d
# Wait 30 seconds for ES to fully start
curl http://localhost:9200/_cluster/health
```

### Fix 3: OpenAI API Key Missing/Invalid
**Symptom:** "Skipping chunk due to empty embedding"

**Fix:**
- Check `backend/src/main/resources/application.yaml`
- Verify `OPENAI_API_KEY` environment variable is set
- Test API key: `curl https://api.openai.com/v1/models -H "Authorization: Bearer $OPENAI_API_KEY"`

### Fix 4: Document Not Readable by Tika
**Symptom:** "No content extracted from document"

**Fix:**
- Try a simple .txt file first
- If PDF: ensure it's text-based (not scanned images)
- Check Tika version supports your document format

## Expected Behavior (Success Case)

When everything works correctly, you should see:

1. **Upload Document**
   - ✅ INDEXING logs show N chunks indexed
   - ✅ Each chunk shows readable content
   - ✅ "Successfully indexed N chunks"

2. **Ask Question**
   - ✅ VECTOR SEARCH returns results
   - ✅ KEYWORD SEARCH returns results
   - ✅ Retrieved chunks contain relevant text
   - ✅ Chat response references the document

3. **Session Isolation**
   - ✅ Documents from Session A only visible in Session A
   - ✅ Searching in Session B doesn't return Session A's docs
   - ✅ sessionId is consistent throughout (indexing → search → response)

---

**Created:** 2026-02-11
**Purpose:** Debug RAG search retrieval issues with full visibility
