# Backend Improvements Design Document

**Date:** 2026-02-15
**Author:** AI Assistant + User
**Status:** Approved for Implementation

## Executive Summary

This document outlines comprehensive improvements to the NotebookLM backend to fix critical bugs, remove dead code, and implement consistent semantic search architecture across all features.

## Problem Statement

### Issues Identified

1. **Dead Code**: MemoryController (5 classes, ~166 LOC) unused by frontend
2. **Resource Leak**: Session deletion leaves orphaned Elasticsearch data (hundreds of chunks per session)
3. **No Relevance Filtering**: Query reformulation uses fixed 5-message window regardless of topic
4. **CRITICAL BUG**: MemoryService.getRelevantMemories() ignores query parameter, returns by importance only
5. **Inconsistent Architecture**: Mixed approach (Elasticsearch for docs, in-memory for memories/chat)
6. **Direct ChatModel Usage**: Services directly inject ChatModel instead of using agent abstraction

## Solution Architecture

### Core Principle: Elasticsearch Hybrid Search for All Semantic Retrieval

**Unified Pipeline:**
```
User Query
    │
    ├─> Embed Query (EmbeddingService)
    │
    ├─> Vector Search (Elasticsearch ANN)
    ├─> Keyword Search (BM25)
    │
    ├─> RRF Fusion (combine rankings)
    │
    ├─> Cross-Encoder Reranking (LLM-based relevance)
    │
    └─> Top-K Results
```

### New Elasticsearch Indices

| Index | Purpose | Schema |
|-------|---------|--------|
| `notebooklm-chunks` | Document chunks (existing) | `embedding`, `content`, `title`, `sessionId`, `documentId` |
| `notebooklm-chat-messages` | Chat history (new) | `embedding`, `content`, `role`, `sessionId`, `timestamp` |
| `notebooklm-memories` | Extracted memories (new) | `embedding`, `memoryContent`, `memoryType`, `importance`, `sessionId` |

### Agent Abstraction Pattern

**Current:** Services directly use `ChatModel`
```java
@Service
public class CrossEncoderReranker {
    private final ChatModel chatModel; // ❌ Direct dependency

    public List<ScoredChunk> rerank(...) {
        String response = chatModel.chat(prompt); // ❌ Unstructured
    }
}
```

**New:** Services use typed agent interfaces
```java
// Agent interface with structured output
public interface CrossEncoderRerankerAgent {
    @UserMessage("Score passages...")
    List<Double> scorePassages(String query, List<String> passages);
}

// Service uses agent
@Service
public class CrossEncoderReranker {
    private final CrossEncoderRerankerAgent agent; // ✅ Typed interface

    public List<ScoredChunk> rerank(...) {
        List<Double> scores = agent.scorePassages(query, texts); // ✅ Type-safe
    }
}
```

## Implementation Phases

### Phase 1: Remove MemoryController (1 hour)

**Goal:** Remove unused REST API and related code

**Files to Delete:**
- MemoryController.java
- CreateMemoryRequest.java
- MemoryResponse.java
- MemoryNotFoundException.java
- MemoryAccessDeniedException.java

**Files to Modify:**
- MemoryService.java (remove unused methods)
- MemoryServiceImpl.java (remove unused methods)

**Impact:** ~166 lines of dead code removed

---

### Phase 2: Fix Session Deletion (2 hours)

**Goal:** Clean up ALL Elasticsearch indices when session is deleted

**Current State:**
```java
public void deleteSession(UUID sessionId) {
    sessionRepository.delete(session); // Only SQLite
    // ❌ Elasticsearch chunks remain orphaned
}
```

**New Implementation:**
```java
public void deleteSession(UUID sessionId) {
    // Clean up ALL Elasticsearch indices
    documentChunkIndexService.deleteBySessionId(sessionId);
    chatMessageIndexService.deleteBySessionId(sessionId);
    memoryIndexService.deleteBySessionId(sessionId);

    // Then delete from SQLite (cascade handles child tables)
    sessionRepository.delete(session);
}
```

**Impact:** No more orphaned data, proper resource cleanup

---

### Phase 3: Chat Message Hybrid Search (6 hours)

**Goal:** Index chat messages in Elasticsearch and use hybrid search for query reformulation

**New Classes:**

1. **ChatMessageDocument.java** (Elasticsearch model)
```java
@Data
@Builder
public class ChatMessageDocument {
    private String id;
    private UUID sessionId;
    private String role;
    private String content;
    private List<Float> embedding;
    private Long timestamp;
}
```

2. **ChatMessageIndexService.java** (implements ElasticsearchIndexOperations)
- Pattern: Same as DocumentChunkIndexService
- Index: `notebooklm-chat-messages`
- Methods: initIndex(), indexMessages(), vectorSearch(), keywordSearch(), deleteBySessionId()

3. **ChatHistoryHybridSearchService.java**
- Implements same pipeline as HybridSearchService
- Vector search + BM25 + RRF fusion + cross-encoder
- Returns top-K relevant messages for query reformulation

**Integration Points:**
- ChatServiceImpl.streamChat() → auto-index messages after saving to SQLite
- QueryReformulationServiceImpl → use hybrid search instead of fixed window

**Configuration:**
```yaml
rag:
  query-reformulation:
    candidate-pool-multiplier: 4  # Fetch 20 for top-5
  chat-message-index:
    enabled: true
    index-name: notebooklm-chat-messages
```

---

### Phase 4: Memory Hybrid Search (6 hours)

**Goal:** Fix CRITICAL bug - implement semantic search for memories

**Current Bug:**
```java
public List<Memory> getRelevantMemories(UUID sessionId, String query, int limit) {
    // ❌ IGNORES query parameter!
    return memoryRepository.findTopMemoriesBySessionId(sessionId, limit);
}
```

**New Implementation:**

1. **MemoryDocument.java** (Elasticsearch model)
```java
@Data
@Builder
public class MemoryDocument {
    private String id;
    private UUID sessionId;
    private String memoryContent;
    private String memoryType;
    private Float importance;
    private List<Float> embedding;
}
```

2. **MemoryIndexService.java**
- Pattern: Same as DocumentChunkIndexService
- Index: `notebooklm-memories`

3. **MemoryHybridSearchService.java**
- Hybrid search pipeline
- **Special:** Hybrid scoring = cross-encoder (70%) + importance field (30%)

4. **Updated MemoryServiceImpl.getRelevantMemories()**
```java
public List<Memory> getRelevantMemories(UUID sessionId, String query, int limit) {
    // ✅ Uses query for semantic search
    List<MemoryDocument> docs = memoryHybridSearchService.search(sessionId, query, limit);
    return fetchEntitiesFromSQLite(docs);
}
```

**Backfill Strategy:**
- On startup: Check if memories exist in SQLite but not in Elasticsearch
- Generate embeddings for existing memories
- Index asynchronously

**Configuration:**
```yaml
rag:
  memory:
    similarity-weight: 0.7  # 70% semantic, 30% importance
    candidate-pool-multiplier: 3
  memory-index:
    backfill-on-startup: true
```

**Impact:** CRITICAL bug fixed, chat quality significantly improved

---

### Phase 5: Refactor ChatModel into Agents (3 hours)

**Goal:** Extract all direct ChatModel usages into agent interfaces

**Pattern:** Follow MemoryExtractionAgent approach using LangChain4j AiServices

**Agents to Create:**

1. **CrossEncoderRerankerAgent.java**
```java
public interface CrossEncoderRerankerAgent {
    @SystemMessage("Score the relevance of each passage...")
    @UserMessage("Query: {{query}}\n\nPassages:\n{{passages}}")
    List<Double> scorePassages(String query, List<String> passages);
}
```

2. **ChatCompactionAgent.java**
```java
public interface ChatCompactionAgent {
    @SystemMessage("Summarize the following conversation...")
    @UserMessage("Messages:\n{{messages}}")
    String summarizeMessages(List<ChatMessage> messages);
}
```

3. **Update AiAgentConfig.java**
```java
@Bean
public CrossEncoderRerankerAgent crossEncoderRerankerAgent(ChatModel chatModel) {
    return AiServices.builder(CrossEncoderRerankerAgent.class)
        .chatLanguageModel(chatModel)
        .build();
}
```

**Services to Refactor:**
- CrossEncoderReranker.java → use CrossEncoderRerankerAgent
- QueryReformulationServiceImpl.java → verify uses QueryReformulationAgent (may already exist)
- ChatCompactionService.java → use ChatCompactionAgent

**Benefits:**
- Consistent abstraction across all LLM interactions
- Type-safe structured outputs
- Easier mocking in tests
- Clear separation of prompts from business logic

---

### Phase 6: Document Compact API (30 minutes)

**Goal:** Clarify compact endpoint is automatic with manual override for advanced users

**Change:**
```java
/**
 * Forces compaction of chat history (ADVANCED).
 *
 * Compaction is automatically triggered when chat history exceeds
 * 30 messages or 3000 tokens. Manual use is only needed for testing.
 */
@PostMapping("/compact")
public ResponseEntity<Void> forceCompaction(@PathVariable UUID sessionId) {
    compactionService.compact(sessionId);
    return ResponseEntity.ok().build();
}
```

## CrossEncoderReranker Generalization

**Challenge:** Currently hardcoded to DocumentChunk type

**Solution:** Use content extractor function
```java
public <T> List<ScoredItem<T>> rerank(
    String query,
    List<T> candidates,
    Function<T, String> contentExtractor,
    int topK
) {
    // Extract text using contentExtractor.apply(item)
    // Score and return generic ScoredItem<T>
}
```

**Usage:**
```java
// For document chunks
reranker.rerank(query, chunks, DocumentChunk::getContent, topK);

// For chat messages
reranker.rerank(query, messages, ChatMessageDocument::getContent, topK);

// For memories
reranker.rerank(query, memories, MemoryDocument::getMemoryContent, topK);
```

## Testing Strategy

### Unit Tests
- All new services with ≥80% coverage
- Mock Elasticsearch index services
- Mock agent interfaces

### Integration Tests (Testcontainers)
- SessionServiceImplIntegrationTest → verify ES cleanup
- ChatHistoryHybridSearchServiceIntegrationTest → verify hybrid search
- MemoryHybridSearchServiceIntegrationTest → verify semantic search + hybrid scoring

### Manual E2E Testing
1. Create session
2. Upload document
3. Chat about document → verify memory extraction
4. Chat about different topic → verify only relevant memories returned
5. Delete session → verify all 3 ES indices cleaned
6. Check ES: `GET /notebooklm-*/_count` → verify counts decrease

## Verification Commands

```powershell
# Code quality
.\gradlew spotlessApply check

# Test coverage
.\gradlew jacocoTestReport
# View: build/reports/jacoco/test/html/index.html

# Integration tests
.\gradlew integrationTest
```

## Success Criteria

| Metric | Target |
|--------|--------|
| Dead code removed | 5 files, ~166 LOC |
| Elasticsearch cleanup | All 3 indices cleaned on session deletion |
| Memory retrieval accuracy | Uses query parameter (semantic search) |
| Test coverage | ≥80% |
| Architecture consistency | All semantic search uses Elasticsearch hybrid pipeline |
| Agent abstraction | No direct ChatModel usage in services |

## Rollback Plan

| Phase | Rollback Action |
|-------|----------------|
| Phase 1 | Restore deleted files from git |
| Phase 2 | Remove ES cleanup calls |
| Phase 3 | Revert to fixed message window |
| Phase 4 | Revert to importance-only ranking |
| Phase 5 | Revert to direct ChatModel usage |
| Phase 6 | Remove documentation |

## Configuration Changes

**application.yaml additions:**
```yaml
rag:
  query-reformulation:
    enabled: true
    history-window: 5
    candidate-pool-multiplier: 4

  chat-message-index:
    enabled: true
    index-name: notebooklm-chat-messages

  memory:
    enabled: true
    similarity-weight: 0.7
    candidate-pool-multiplier: 3

  memory-index:
    enabled: true
    index-name: notebooklm-memories
    backfill-on-startup: true
```

## Impact Summary

### Before
- 166 LOC dead code
- ~500 orphaned ES chunks per deleted session
- Query reformulation: fixed 5-message window
- Memory retrieval: importance-only (ignores query) 🔴
- Mixed architecture (ES + in-memory)
- Direct ChatModel dependencies

### After
- Zero dead code
- Complete resource cleanup (3 ES indices)
- Query reformulation: semantically relevant messages
- Memory retrieval: semantic search (70%) + importance (30%) ✅
- Consistent ES hybrid search architecture
- Typed agent abstraction for all LLM interactions

## References

- Root CLAUDE.md: Project overview, SOLID principles
- Backend CLAUDE.md: Java/Spring coding standards, Elasticsearch patterns
- Implementation Plan: `C:\Users\feich\.claude\plans\delightful-gliding-dove.md`
