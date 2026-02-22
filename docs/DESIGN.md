# NotebookLM Design Document

> **Last Updated**: 2026-02-21
> **Status**: Active Development

## Table of Contents

1. [System Architecture](#1-system-architecture)
2. [RAG Pipeline](#2-rag-pipeline)
3. [Chat System](#3-chat-system)
4. [Memory Service](#4-memory-service)
5. [Chinese Language Support](#5-chinese-language-support)
6. [Resilience & Observability](#6-resilience--observability)
7. [Future Improvements](#7-future-improvements)

---

## 1. System Architecture

### High-Level Diagram

```
+---------------------------------------------------------------------+
|                     Angular 20 Frontend (SSR)                        |
|  +------------+ +------------+ +------------+ +----------+          |
|  |  Chat UI   | |  Document  | |  Session   | |   Mode   |          |
|  | (Streaming)| |   Upload   | |  Manager   | | Selector |          |
|  +------------+ +------------+ +------------+ +----------+          |
+---------------------------------------------------------------------+
                              | HTTP REST / SSE
                              v
+---------------------------------------------------------------------+
|                    Spring Boot 4.0.2 Backend                         |
|  +----------------------------------------------------------------+ |
|  |                      API Layer                                  | |
|  |   REST: /sessions, /documents     SSE: /chat/stream            | |
|  +----------------------------------------------------------------+ |
|  +----------------------------------------------------------------+ |
|  |                    Service Layer                                | |
|  |  SessionService | DocumentService | ChatService | RAGService   | |
|  +----------------------------------------------------------------+ |
|  +----------------------------------------------------------------+ |
|  |                 LangChain4j Integration                         | |
|  |  +---------------+  +---------------+  +--------------+        | |
|  |  | ChatLanguage  |  |   Embedding   |  |   Content    |        | |
|  |  |    Model      |  |     Model     |  |  Retriever   |        | |
|  |  |  (GPT-4o-mini)|  | (text-embed-3)|  | (Hybrid RAG) |        | |
|  |  +---------------+  +---------------+  +--------------+        | |
|  +----------------------------------------------------------------+ |
|  +----------------------------------------------------------------+ |
|  |                     RAG Pipeline                                | |
|  |  Document -> Tika Parse -> Chunk -> Embed -> Index              | |
|  |  Query -> Embed -> Vector+BM25 -> RRF -> Rerank(TEI) -> Context | |
|  +----------------------------------------------------------------+ |
+--------------+------------------+------------------+----------------+
               |                  |                  |
       +-------+-------+  +------+-------+  +-------+-------+
       |    SQLite      |  | Elasticsearch|  | TEI Reranker  |
       |----------------|  |--------------|  |---------------|
       | - Sessions     |  | - Chunks     |  | bge-reranker  |
       | - Documents    |  | - Chat Msgs  |  | -base         |
       | - ChatMessages |  | - Memories   |  | (port 8090)   |
       | - ChatSummary  |  | - Embeddings |  |               |
       | - Memories     |  | - BM25 Index |  |               |
       +----------------+  +--------------+  +---------------+
```

### Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Frontend | Angular (SSR) + Tailwind CSS | 20 |
| Backend | Spring Boot + LangChain4j | 4.0.2 / 1.11.0 |
| LLM | OpenAI GPT (chat) + text-embedding-3-large (embeddings) | - |
| Search | Elasticsearch (vectors + BM25) | 9.1.4 |
| Reranker | Hugging Face TEI with BAAI/bge-reranker-base | cpu-1.9 |
| Database | SQLite (sessions/chat) | - |
| Document Parsing | Apache Tika | 3.2.3 |
| Resilience | Resilience4j | 2.2.0 |
| Testing | JUnit 5 + Testcontainers | 1.20.4 |

---

## 2. RAG Pipeline

### 2.1 Document Ingestion

```
Upload -> Tika Parse -> Chunk (400 tokens, 50 overlap) -> Metadata Enrich -> Embed -> Index
```

**Chunking**: Fixed-size token windows with overlap for context continuity.

**Metadata Enrichment**: Each chunk is augmented with document-level context before embedding:

```
[Document: Q4 Financial Report 2024]
[Section: Revenue Analysis]
[Keywords: revenue, growth, quarterly, projections]

The company achieved record revenue of $1.2B in Q4...
```

This `enrichedContent` is embedded instead of raw content, improving retrieval accuracy by giving the embedding model document context.

**Elasticsearch Index Schema** (`notebooklm-chunks`):

| Field | Type | Purpose |
|-------|------|---------|
| `id` | keyword | Chunk identifier |
| `sessionId` | keyword | Session isolation filter |
| `documentId` | keyword | Source document reference |
| `fileName` | text | Original file name |
| `documentTitle` | text (ik_max_word) | Extracted document title |
| `sectionTitle` | text (ik_max_word) | Section/chapter header |
| `content` | text (ik_max_word) | Raw chunk text |
| `enrichedContent` | text (ik_max_word) | Metadata-prefixed content |
| `titleEmbedding` | dense_vector (3072d) | Title embedding |
| `contentEmbedding` | dense_vector (3072d) | Content embedding |
| `chunkIndex` | integer | Position in document |

### 2.2 Hybrid Search

Three-signal fusion combining semantic and lexical search:

```
User Query
    |
    v
[1] Embed query (text-embedding-3-large, 3072d)
    |
    +---> [2a] Vector search: titleEmbedding (kNN)
    +---> [2b] Vector search: contentEmbedding (kNN)
    +---> [2c] BM25 keyword search (multi-field: title^3, section^2, content)
    |
    v
[3] Application-side RRF fusion
    score = SUM( 1/(60 + rank_i) ) for each retriever
    |
    v
[4] Source anchoring boost (for follow-up queries)
    |
    v
Top K*2 candidates for reranking
```

**Why application-side RRF**: Elasticsearch's native RRF retriever requires a Platinum license. The application-side implementation provides identical scoring with full control over the fusion process.

**Session Isolation**: All search queries filter by `sessionId` at the Elasticsearch query level, ensuring strict multi-tenancy. Verified by integration tests covering vector search, keyword search, and hybrid search paths.

### 2.3 Reranking

Multi-stage reranking pipeline with strategy pattern:

```
RRF Candidates -> Reranker (interface) -> DiversityReranker -> Final top-K
                      |
                      +-- TeiCrossEncoderReranker (default)
                      +-- LlmPromptReranker (deprecated)
```

**TEI Cross-Encoder** (default, `rag.reranking.strategy: tei`):
- Model: `BAAI/bge-reranker-base` served by Hugging Face TEI
- Runs locally in Docker on port 8090, zero API cost
- ~10-60x faster than LLM prompt reranking
- Deterministic results
- Circuit breaker + retry; fallback returns RRF scores as-is

**LLM Prompt Reranker** (deprecated, `rag.reranking.strategy: llm`):
- Uses OpenAI GPT to score passage relevance via prompt
- Non-deterministic, has API cost
- Kept as fallback option

**Diversity Reranker**: Round-robin selection across documents to ensure multi-document representation in results. Guarantees minimum chunks per document when applicable.

```
Input (sorted by relevance):
  Doc A: [chunk1, chunk3, chunk5]
  Doc B: [chunk2, chunk4]
  Doc C: [chunk6]

Round-robin output: [A.chunk1, B.chunk2, C.chunk6, A.chunk3, B.chunk4, A.chunk5]
```

**Retrieval by Mode**:

| Mode | Top-K Chunks | Focus |
|------|-------------|-------|
| EXPLORING | 8 | Broad discovery, diverse sources |
| RESEARCH | 4 | High relevance, precise citations |
| LEARNING | 6 | Progressive understanding |

### 2.4 Query Reformulation & Context-Aware RAG

Handles follow-up queries that reference previous responses:

```
User asks Q2 (follow-up to R1)
    |
    v
QueryReformulationServiceImpl.reformulate()
    1. Fetch last 2 messages from DB (always, regardless of semantic match)
    2. Fetch up to historyWindow=5 semantically relevant messages (hybrid search)
    3. Merge: recent DB messages first, fill with semantic (deduplicated)
    4. Parse last ASSISTANT's retrievedContextJson -> anchorDocumentIds
    5. Call QueryReformulationAgent(recentExchange, conversationHistory, query)
    6. Agent returns: {needsReformulation, isFollowUp, query}
    |
    v
ReformulatedQuery { query, isFollowUp, anchorDocumentIds }
    |
    v
If isFollowUp=true:
    HybridSearch with source anchoring boost (+0.3 to anchor doc chunks)
Else:
    Standard HybridSearch
```

**Key types**:
- `ReformulatedQuery(query, isFollowUp, anchorDocumentIds)` — rich return from reformulation
- `retrievedContextJson` — stored on ASSISTANT messages, tracks which chunks backed each response

**Why recency bias**: Vague follow-ups ("Can you elaborate?") score low in semantic search against the immediately preceding exchange. Always including the last 2 messages ensures the reformulation agent sees the relevant context.

**Configuration**:

```yaml
rag:
  query-reformulation:
    enabled: true
    history-window: 5
    min-recent-messages: 2
  retrieval:
    source-anchoring-enabled: true
    source-anchoring-boost: 0.3
```

### 2.5 Context Assembly

Retrieved chunks are formatted for the LLM with source attribution:

```
=== SOURCES ===

[1] Financial Report 2024 (financial-report.pdf) - Section: Revenue Analysis
The company achieved record revenue of $1.2B in Q4...

[2] Product Roadmap (roadmap.md) - Section: Q1 2025 Plans
Key features planned for Q1 include...

=== END SOURCES ===
```

---

## 3. Chat System

### 3.1 Interaction Modes

| Mode | Behavior | System Prompt Focus |
|------|----------|---------------------|
| **EXPLORING** | Broad discovery, tangential suggestions | Encourage curiosity, suggest related topics |
| **RESEARCH** | Precise citations, fact-focused | Require citations, flag uncertainty |
| **LEARNING** | Socratic method, builds understanding | Ask questions, provide explanations |

### 3.2 Chat Compaction

```
Context Window Budget: ~4000 tokens
  System Prompt + Mode Instructions          (~500 tokens)
  Session Memories (key facts)               (~300 tokens)
  Compacted History (summaries)              (~500 tokens)
  Recent Messages (sliding window, 10 msgs)  (~1500 tokens)
  Retrieved RAG Context                      (~1200 tokens)
```

**Compaction triggers**:
- Total tokens > 3000
- Message count > 30
- Explicit user request (`POST /api/sessions/{id}/compact`)

**Process**:
1. Keep last 10 messages in full
2. Summarize older messages in batches of 20
3. Store summaries in `ChatSummary` table
4. Mark original messages as compacted

### 3.3 SSE Streaming

```
POST /api/sessions/{id}/chat/stream
Accept: text/event-stream

Event types:
  event: token     -> {"content": "The"}
  event: citation  -> {"source": "doc.pdf", "page": 1, "text": "..."}
  event: done      -> {"messageId": "uuid", "promptTokens": 500, "completionTokens": 150}
  event: error     -> {"errorId": "uuid", "message": "Service temporarily unavailable"}
```

---

## 4. Memory Service

Long-term knowledge extracted from conversations, persisting across chat compaction.

### Memory Types

| Type | Description | Example |
|------|-------------|---------|
| `fact` | Factual information from documents | "The project deadline is March 15th" |
| `preference` | User preferences or working style | "User prefers bullet-point summaries" |
| `insight` | Connections or conclusions drawn | "Revenue correlates with marketing spend in Q3" |

### Architecture

```
ChatServiceImpl.streamChat()
    -> onCompleteResponse()
        -> MemoryService.extractAndSaveAsync()
            1. MemoryExtractionAgent (LLM) extracts memories from Q&A exchange
            2. Deduplicate against existing memories (exact + similarity)
            3. Save to SQLite + index in Elasticsearch (notebooklm-memories)

ChatServiceImpl.buildConversationContext()
    -> MemoryService.getRelevantMemories(sessionId, query, limit)
        1. Hybrid search in Elasticsearch (vector + BM25 + RRF)
        2. Scoring: semantic similarity (70%) + importance field (30%)
        3. Return top-K relevant memories
    -> MemoryService.buildMemoryContext(memories)
        -> Formatted string injected into system prompt
```

**Memory context format** (injected into LLM prompt):

```
Relevant memories from this session:
- [FACT] The project deadline is March 15th (importance: 0.9)
- [PREFERENCE] User prefers concise bullet-point summaries (importance: 0.7)
- [INSIGHT] Q3 revenue increase correlates with marketing campaign launch (importance: 0.8)
```

**Configuration**:

```yaml
rag:
  memory:
    enabled: true
    max-per-session: 50
    extraction-threshold: 0.3
    context-limit: 5
    similarity-weight: 0.7
```

---

## 5. Chinese Language Support

### IK Analysis Plugin

The default Elasticsearch `standard` analyzer tokenizes Chinese character-by-character, producing poor BM25 results. The IK Analysis plugin provides proper Chinese word segmentation:

| Analyzer | Use Case | Example: "人工智能是未来的技术" |
|----------|----------|-------------------------------|
| `ik_max_word` | Indexing (fine-grained) | ["人工智能", "人工", "智能", "是", "未来", "的", "技术"] |
| `ik_smart` | Searching (coarser) | ["人工智能", "是", "未来", "的", "技术"] |

**Configuration** (`application.yaml`):

```yaml
app:
  elasticsearch:
    text-analyzer: ik_max_word
    text-search-analyzer: ik_smart
```

These apply to all text fields across all three indexes (chunks, chat-messages, memories).

### Setup

The custom `Dockerfile.elasticsearch` installs the IK plugin. Rebuild required:

```bash
cd backend
docker-compose down
docker volume rm backend_elasticsearch-data  # Analyzer change needs fresh index
docker-compose build
docker-compose up -d
```

Verify: `curl http://localhost:9200/_cat/plugins` should show `analysis-ik`.

### Token Estimation

CJK characters are denser than English (~1.8 chars/token vs ~4 chars/token). The embedding service uses conservative estimates to stay under the 8,192 token limit:

| Parameter | Value |
|-----------|-------|
| `CHARS_PER_TOKEN_ESTIMATE` | 1.8 |
| `MAX_TOKENS_PER_EMBEDDING` | ~6,000 |

---

## 6. Resilience & Observability

### Circuit Breakers

| Service | Failure Threshold | Wait Duration | Rationale |
|---------|-------------------|---------------|-----------|
| OpenAI | 30% | 60s | Paid API, sensitive to cost |
| Elasticsearch | 50% | 10s | Local, recovers fast |
| TEI Reranker | 50% | 10s | Local, recovers fast |

### Retry Configuration

| Service | Max Attempts | Wait | Backoff |
|---------|-------------|------|---------|
| OpenAI | 3 | 2s | Exponential (x2) |
| Elasticsearch | 3 | 500ms | Fixed |
| TEI | 2 | 500ms | Fixed |

### Graceful Degradation

- **Search failure**: Return empty results, never propagate ES errors to users
- **TEI failure**: Return candidates with existing RRF scores (no cascading to OpenAI)
- **Memory extraction failure**: Log and continue (non-blocking to chat flow)

### Metrics

| Category | Examples |
|----------|---------|
| API | `http.server.requests` (built-in), SSE connection count |
| LLM | Request duration, token usage, error count |
| RAG | Search duration by type, documents retrieved, rerank duration |
| Documents | Parse duration, chunk count, processing errors |
| Storage | ES query/index duration, SQLite query duration |
| Compaction | Duration, messages processed, tokens saved |
| Memory | Extraction count, saved count, retrieved count |
| Reranker | TEI invocations, top score, fallback count |

All service-layer metrics use `@Timed` annotation. Controllers rely on built-in `http.server.requests`.

---

## 7. Future Improvements

### Hierarchical Search (for large document collections)

When a session has many documents (5+), add a two-level search:
1. **Level 1**: Search document summaries/embeddings to find top-N relevant documents
2. **Level 2**: Chunk-level hybrid search filtered to those documents

Skip Level 1 for sessions with 3 or fewer documents.

### LangGraph4j Agent Orchestration

Replace the linear chat flow with a LangGraph4j state machine for:
- Conditional branching (skip search if no documents)
- Multi-step reasoning with tool calls
- Autonomous research without user input
- Multi-agent collaboration (researcher + critic)

### Additional Features

- **Web Page Ingestion**: Parse URLs, extract content with JSoup
- **YouTube Transcript Support**: Extract and chunk by timestamp
- **Audio Generation**: Podcast-style two-voice discussions via TTS
- **Collaborative Sessions**: Multi-user with shared documents via WebSocket
- **Export**: Chat as Markdown/PDF, document summaries, extracted memories

### Native Elasticsearch RRF

The current application-side RRF could be replaced with Elasticsearch's native RRF retriever API (available in 8.16+) to reduce 3 queries to 1. This requires a Platinum license or Elastic Cloud, so it remains deferred.

---

*This document consolidates all design documentation. For coding standards, see `CLAUDE.md` (root), `backend/CLAUDE.md`, and `frontend/CLAUDE.md`.*
