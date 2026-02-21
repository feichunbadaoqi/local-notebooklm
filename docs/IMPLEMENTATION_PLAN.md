# NotebookLM Clone - Implementation Plan

> **Last Updated**: 2026-02-21
> **Status**: Active Development

## Table of Contents
1. [Overview](#overview)
2. [LangGraph4j vs LangChain4j Analysis](#langgraph4j-vs-langchain4j-analysis)
3. [Architecture](#architecture)
4. [Metrics & Observability](#metrics--observability)
5. [Resilience & Error Handling](#resilience--error-handling)
6. [Implementation Phases](#implementation-phases)
7. [API Design](#api-design)
8. [Testing Strategy](#testing-strategy)

---

## Overview

Build a localhost NotebookLM clone with:
- **Frontend**: Angular 20 with SSR - Chat UI with document management
- **Backend**: Spring Boot 4.0.2 + LangChain4j - Hybrid RAG system
- **Storage**: SQLite (sessions, chat history) + Elasticsearch (vector embeddings)
- **LLM**: OpenAI GPT-4o-mini (chat) + text-embedding-3-small (embeddings)
- **API**: REST for CRUD, SSE for chat streaming

### Product Features
- Chat-style UI for document Q&A (like Google NotebookLM)
- Document uploads: Word (.docx), PDF, EPUB with text extraction
- Interaction modes: EXPLORING, RESEARCH, LEARNING
- Session persistence with chat history compaction
- Real-time streaming responses via SSE

---

## LangGraph4j vs LangChain4j Analysis

### What They Are

| Framework | Description |
|-----------|-------------|
| **LangChain4j** | Core library for LLM integration - provides RAG, tool calling, prompt templates, chat memory, and basic agents |
| **LangGraph4j** | Extension for stateful, multi-agent systems - adds cyclic graphs, checkpoints, conditional routing |

### Key Differences

| Aspect | LangChain4j | LangGraph4j |
|--------|-------------|-------------|
| **Architecture** | Linear DAGs (Directed Acyclic Graphs) | Cyclic graphs with loops |
| **State Management** | Implicit (automatic data passing) | Explicit (you define state object) |
| **Best For** | Predictable workflows, RAG, chatbots | Multi-agent, dynamic, stateful workflows |
| **Complexity** | Simple to moderate | Moderate to complex |
| **Checkpointing** | Not built-in | Built-in persistence & replay |
| **Visualization** | Not built-in | PlantUML/Mermaid graph visualization |

### Analysis for NotebookLM Clone

**Our Requirements:**
1. RAG pipeline (retrieve → generate)
2. Multiple interaction modes (EXPLORING/RESEARCH/LEARNING)
3. Chat history with compaction
4. Session persistence
5. Memory extraction

**LangChain4j Can Handle:**
- RAG pipeline with `EmbeddingStore` and `ContentRetriever`
- Chat memory with `ChatMemory` interface
- Mode-specific prompts via `SystemMessage`
- Basic agent with `AiServices` and tools

**LangGraph4j Adds Value For:**
- Complex retry logic (if retrieval fails, try different approach)
- Multi-step reasoning with conditional branching
- Long-running processes with checkpointing
- Future multi-agent expansion

### Recommendation: Start with LangChain4j Only

**Rationale:**
1. **Simpler Architecture**: LangChain4j's `AiServices` with RAG is sufficient for our chat + document Q&A use case
2. **Faster Development**: Less boilerplate, easier to test and debug
3. **Mode Switching**: Can be handled with different `SystemMessage` prompts per mode
4. **Chat Compaction**: Can be implemented as a service without graph orchestration
5. **Upgrade Path**: Can add LangGraph4j later if we need complex agent behaviors

**When to Add LangGraph4j:**
- If we add autonomous research agents that run multi-step investigations
- If we need sophisticated retry/fallback logic between different LLMs
- If we add collaborative multi-agent features (e.g., "researcher" + "critic" agents)

### Revised Technology Stack

```
LangChain4j (Core)
├── langchain4j-core                    # Core abstractions
├── langchain4j-open-ai                 # OpenAI integration
├── langchain4j-embeddings-all-minilm   # Local fallback embeddings
├── langchain4j-document-parser-apache-tika  # Document parsing
└── langchain4j-spring-boot-starter     # Spring Boot integration

Optional (Add Later If Needed)
└── langgraph4j-core                    # Graph orchestration
```

---

## Architecture

### High-Level System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Angular 20 Frontend (SSR)                        │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐ │
│  │   Chat UI    │ │   Document   │ │   Session    │ │    Mode    │ │
│  │  (Streaming) │ │    Upload    │ │   Manager    │ │  Selector  │ │
│  └──────────────┘ └──────────────┘ └──────────────┘ └────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                              │ HTTP REST / SSE
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Spring Boot 4.0.2 Backend                         │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                      API Layer                                  │ │
│  │   REST: /sessions, /documents     SSE: /chat/stream            │ │
│  │   ┌──────────────────────────────────────────────────────────┐ │ │
│  │   │  Metrics: request_count, latency_seconds, error_count    │ │ │
│  │   └──────────────────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                    Service Layer                                │ │
│  │  SessionService │ DocumentService │ ChatService │ RAGService   │ │
│  │   ┌──────────────────────────────────────────────────────────┐ │ │
│  │   │  Metrics: llm_latency, embedding_latency, rag_latency    │ │ │
│  │   │  Circuit Breakers: OpenAI, Elasticsearch                 │ │ │
│  │   └──────────────────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                 LangChain4j Integration                         │ │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐ │ │
│  │  │  ChatLanguage   │  │   Embedding     │  │    Content     │ │ │
│  │  │     Model       │  │     Model       │  │   Retriever    │ │ │
│  │  │  (GPT-4o-mini)  │  │ (text-embed-3)  │  │  (Hybrid RAG)  │ │ │
│  │  └─────────────────┘  └─────────────────┘  └────────────────┘ │ │
│  └────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                     RAG Pipeline                                │ │
│  │  Document → Tika Parse → Chunk → Embed → Index                 │ │
│  │  Query → Embed → Vector+BM25 → RRF → Rerank(TEI) → Context    │ │
│  └────────────────────────────────────────────────────────────────┘ │
└──────────────┬──────────────────┬──────────────────┬──────────────┘
               │                  │                  │
       ┌───────┴───────┐  ┌──────┴───────┐  ┌───────┴───────┐
       │    SQLite     │  │ Elasticsearch │  │  TEI Reranker │
       │───────────────│  │──────────────│  │───────────────│
       │ • Sessions    │  │ • Chunks     │  │ bge-reranker  │
       │ • Documents   │  │ • Embeddings │  │ -base         │
       │ • ChatMessages│  │ • BM25 Index │  │ (port 8090)   │
       │ • ChatSummary │  │              │  │               │
       │ • Memories    │  │              │  │               │
       └───────────────┘  └──────────────┘  └───────────────┘
```

### Reranking Strategy

The reranking stage applies semantic scoring to RRF-fused candidates before diversity reranking. Two strategies are available, selected via `rag.reranking.strategy`:

| Strategy | Implementation | Model | Latency | Cost | Deterministic |
|----------|---------------|-------|---------|------|---------------|
| **`tei`** (default) | `TeiCrossEncoderReranker` | `BAAI/bge-reranker-base` via TEI | ~50-200ms (CPU) | Zero (local) | Yes |
| `llm` | `LlmPromptReranker` (deprecated) | OpenAI GPT via chat prompt | ~1-3s | Token costs | No |

**Architecture:**
- `Reranker` interface abstracts both strategies
- `@ConditionalOnProperty` selects the active bean at startup
- TEI runs as a Docker container (`ghcr.io/huggingface/text-embeddings-inference:cpu-1.9`) on port 8090
- Circuit breaker + retry on TEI calls; fallback returns RRF scores as-is

**TEI API Contract:**
```
POST http://localhost:8090/rerank
Request:  { "query": "...", "texts": ["...", "..."], "raw_scores": false, "truncate": true }
Response: [{ "index": 0, "score": 0.98 }, { "index": 1, "score": 0.02 }]
```

### Interaction Modes (Implemented via System Prompts)

| Mode | Behavior | System Prompt Focus | Retrieval Strategy |
|------|----------|--------------------|--------------------|
| **EXPLORING** | Broad discovery, tangential suggestions | Encourage curiosity, suggest related topics | Top 8 chunks, diverse sources |
| **RESEARCH** | Precise citations, fact-focused | Require citations, flag uncertainty | Top 4 chunks, high relevance |
| **LEARNING** | Socratic method, builds understanding | Ask questions, provide explanations | Top 6 chunks, progressive |

### Chat Compaction Strategy

```
┌────────────────────────────────────────────────────────────┐
│                  Context Window Budget: 4000 tokens        │
├────────────────────────────────────────────────────────────┤
│  System Prompt + Mode Instructions         (~500 tokens)   │
│  Session Memories (key facts)              (~300 tokens)   │
│  Compacted History (summaries)             (~500 tokens)   │
│  Recent Messages (sliding window, 10 msgs) (~1500 tokens)  │
│  Retrieved RAG Context                     (~1200 tokens)  │
└────────────────────────────────────────────────────────────┘

Compaction Triggers:
- Total tokens > 3000
- Message count > 30
- Explicit user request

Compaction Process:
1. Keep last 10 messages in full
2. Summarize older messages in batches of 20
3. Store summaries in ChatSummary table
4. Mark original messages as compacted
```

---

## Metrics & Observability

### Metric Categories

#### 1. API Metrics (Controller Layer)
```java
// Micrometer metrics to emit
api_requests_total{endpoint, method, status}     // Counter
api_request_duration_seconds{endpoint, method}   // Timer/Histogram
api_errors_total{endpoint, error_type}           // Counter
sse_connections_active{session_id}               // Gauge
sse_events_sent_total{event_type}                // Counter
```

#### 2. LLM Metrics (LangChain4j Layer)
```java
llm_request_duration_seconds{model, operation}   // Timer
llm_tokens_used_total{model, type}               // Counter (prompt/completion)
llm_requests_total{model, status}                // Counter
llm_errors_total{model, error_type}              // Counter
```

#### 3. RAG Pipeline Metrics
```java
rag_retrieval_duration_seconds{search_type}      // Timer (vector/keyword/hybrid)
rag_documents_retrieved{search_type}             // Histogram
rag_embedding_duration_seconds                   // Timer
rag_rerank_duration_seconds                      // Timer (if enabled)
```

#### 4. Document Processing Metrics
```java
document_parse_duration_seconds{file_type}       // Timer
document_chunk_count{file_type}                  // Histogram
document_processing_errors_total{file_type}      // Counter
```

#### 5. Storage Metrics
```java
elasticsearch_query_duration_seconds{operation}  // Timer
elasticsearch_index_duration_seconds             // Timer
sqlite_query_duration_seconds{operation}         // Timer
```

#### 6. Chat Compaction Metrics
```java
chat_compaction_duration_seconds                 // Timer
chat_compaction_messages_processed               // Counter
chat_compaction_tokens_saved                     // Counter
```

### Implementation Approach

```java
@Configuration
public class MetricsConfig {

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "notebooklm")
            .commonTags("environment", "${spring.profiles.active}");
    }
}

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MeterRegistry meterRegistry;
    private final Timer llmRequestTimer;

    @PostConstruct
    void initMetrics() {
        llmRequestTimer = Timer.builder("llm_request_duration_seconds")
            .tag("model", "gpt-4o-mini")
            .register(meterRegistry);
    }

    public Flux<String> chat(ChatRequest request) {
        return Mono.fromCallable(() -> Timer.start(meterRegistry))
            .flatMapMany(sample -> {
                // LLM call
                return llmService.streamChat(request)
                    .doOnComplete(() -> sample.stop(llmRequestTimer));
            });
    }
}
```

### Observability Stack

```yaml
# application.yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  metrics:
    export:
      otlp:
        enabled: true
        endpoint: http://localhost:4318/v1/metrics
    tags:
      application: notebooklm
```

---

## Resilience & Error Handling

### Circuit Breaker Pattern

```java
@Configuration
public class ResilienceConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build())
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .build())
            .build());
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> openAiCircuitBreaker() {
        return factory -> factory.configure(builder -> builder
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .failureRateThreshold(30)  // More sensitive for paid API
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build())
            .build(), "openai");
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> elasticsearchCircuitBreaker() {
        return factory -> factory.configure(builder -> builder
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build())
            .build(), "elasticsearch");
    }
}
```

### Retry Configuration

```java
@Configuration
public class RetryConfig {

    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.of(Map.of(
            "openai", Retry.of("openai", io.github.resilience4j.retry.RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .exponentialBackoffMultiplier(2)
                .retryOnException(e -> e instanceof OpenAiHttpException
                    && ((OpenAiHttpException) e).statusCode() == 429)  // Rate limit
                .build()),
            "elasticsearch", Retry.of("elasticsearch", io.github.resilience4j.retry.RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(ElasticsearchException.class)
                .build())
        ));
    }
}
```

### Graceful Degradation

```java
@Service
@RequiredArgsConstructor
public class HybridSearchService {
    private final VectorSearchService vectorSearch;
    private final KeywordSearchService keywordSearch;
    private final CircuitBreakerFactory cbFactory;

    public List<SearchResult> search(String query, UUID sessionId) {
        CircuitBreaker cb = cbFactory.create("elasticsearch");

        try {
            // Try hybrid search
            return cb.run(() -> executeHybridSearch(query, sessionId),
                throwable -> {
                    // Fallback: return empty with warning
                    log.warn("Search failed, returning empty results", throwable);
                    meterRegistry.counter("rag_fallback_total", "reason", "circuit_open").increment();
                    return Collections.emptyList();
                });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
```

### Error Response Model

```java
@Getter
@Builder
public class ApiError {
    private final String errorId;        // UUID for log correlation
    private final String code;           // Machine-readable code
    private final String message;        // User-friendly message
    private final String details;        // Technical details (dev mode only)
    private final Instant timestamp;
    private final String path;

    // Error codes
    public static final String DOCUMENT_PARSE_ERROR = "DOC_PARSE_001";
    public static final String LLM_UNAVAILABLE = "LLM_001";
    public static final String LLM_RATE_LIMITED = "LLM_002";
    public static final String SEARCH_FAILED = "SEARCH_001";
    public static final String SESSION_NOT_FOUND = "SESSION_001";
    public static final String INVALID_REQUEST = "VALIDATION_001";
}
```

### Global Exception Handler

```java
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final MeterRegistry meterRegistry;

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ApiError> handleSessionNotFound(SessionNotFoundException ex, HttpServletRequest request) {
        meterRegistry.counter("api_errors_total", "error_type", "session_not_found").increment();

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.builder()
                .errorId(UUID.randomUUID().toString())
                .code(ApiError.SESSION_NOT_FOUND)
                .message("Session not found")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(OpenAiHttpException.class)
    public ResponseEntity<ApiError> handleOpenAiError(OpenAiHttpException ex, HttpServletRequest request) {
        String errorType = ex.statusCode() == 429 ? "rate_limited" : "llm_error";
        meterRegistry.counter("api_errors_total", "error_type", errorType).increment();

        String message = ex.statusCode() == 429
            ? "Service is temporarily busy. Please try again in a moment."
            : "AI service is currently unavailable. Please try again later.";

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiError.builder()
                .errorId(UUID.randomUUID().toString())
                .code(ex.statusCode() == 429 ? ApiError.LLM_RATE_LIMITED : ApiError.LLM_UNAVAILABLE)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<ApiError> handleDocumentError(DocumentProcessingException ex, HttpServletRequest request) {
        meterRegistry.counter("api_errors_total", "error_type", "document_processing").increment();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiError.builder()
                .errorId(UUID.randomUUID().toString())
                .code(ApiError.DOCUMENT_PARSE_ERROR)
                .message("Failed to process document: " + ex.getUserMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
    }
}
```

### SSE Error Handling

```java
@RestController
@RequestMapping("/api/sessions/{sessionId}/chat")
public class ChatSseController {

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable UUID sessionId, @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);  // 5 min timeout

        emitter.onTimeout(() -> {
            meterRegistry.counter("sse_errors_total", "error_type", "timeout").increment();
            emitter.complete();
        });

        emitter.onError(throwable -> {
            meterRegistry.counter("sse_errors_total", "error_type", "connection_error").increment();
            log.error("SSE error for session {}", sessionId, throwable);
        });

        chatStreamingService.streamResponse(sessionId, request)
            .subscribe(
                chunk -> sendSseEvent(emitter, chunk),
                error -> sendSseError(emitter, error),
                emitter::complete
            );

        return emitter;
    }

    private void sendSseError(SseEmitter emitter, Throwable error) {
        try {
            String errorId = UUID.randomUUID().toString();
            log.error("Chat stream error [{}]", errorId, error);

            emitter.send(SseEmitter.event()
                .name("error")
                .data(Map.of(
                    "errorId", errorId,
                    "message", getUserFriendlyMessage(error)
                )));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
```

---

## Implementation Phases

### Phase 1: Foundation (Backend Setup)

**Goals:** Set up build system, database, basic API structure

**Files to modify:**
- `backend/build.gradle` - Add all dependencies, quality plugins

**Files to create:**
- `backend/config/checkstyle/checkstyle.xml`
- `backend/config/spotbugs/exclude-filter.xml`
- `backend/src/main/resources/application.yaml` - Full configuration
- `backend/src/main/java/.../config/` - Spring configurations
- `backend/src/main/java/.../domain/entity/` - JPA entities
- `backend/src/main/java/.../domain/repository/` - Repositories
- `backend/src/main/java/.../exception/` - Exception classes

**Verification:**
```bash
./gradlew build  # Should compile successfully
./gradlew test   # Basic context loads
```

### Phase 2: Document Processing

**Goals:** Upload, parse, chunk, and index documents

**Files to create:**
- `service/document/DocumentService.java`
- `service/document/DocumentParserService.java` (Tika)
- `service/document/DocumentChunkingService.java`
- `elasticsearch/ElasticsearchIndexService.java`
- `api/rest/DocumentController.java`

**Verification:**
- Upload PDF → check chunks in Elasticsearch
- Upload DOCX → verify text extraction
- Upload EPUB → verify chapter parsing

### Phase 3: RAG Pipeline

**Goals:** Implement hybrid search with RRF fusion

**Files to create:**
- `service/rag/EmbeddingService.java`
- `service/rag/VectorSearchService.java`
- `service/rag/KeywordSearchService.java`
- `service/rag/HybridSearchService.java`
- `service/rag/ReciprocalRankFusionService.java`

**Verification:**
- Query returns relevant chunks
- Vector + keyword results merged correctly

### Phase 4: Chat & Streaming

**Goals:** Implement chat with SSE streaming and mode support

**Files to create:**
- `service/chat/ChatService.java`
- `service/chat/ChatStreamingService.java`
- `service/chat/ModePromptService.java`
- `api/sse/ChatSseController.java`

**Verification:**
- Send message → receive streaming tokens
- Mode switching changes response style
- Chat history persists

### Phase 5: Memory & Compaction

**Goals:** Implement chat compaction and memory extraction

**Files to create:**
- `service/chat/ChatCompactionService.java`
- `service/memory/MemoryService.java`

**Verification:**
- Compaction triggers at threshold
- Summaries stored in database
- Context assembly includes summaries

### Phase 6: Resilience & Metrics

**Goals:** Add circuit breakers, retries, metrics

**Files to create:**
- `config/ResilienceConfig.java`
- `config/MetricsConfig.java`

**Verification:**
- Metrics visible at `/actuator/prometheus`
- Circuit breaker opens on failures
- Graceful degradation works

---

## API Design

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions` | Create session |
| GET | `/api/sessions` | List sessions |
| GET | `/api/sessions/{id}` | Get session |
| PUT | `/api/sessions/{id}` | Update session |
| DELETE | `/api/sessions/{id}` | Delete session |
| PUT | `/api/sessions/{id}/mode` | Change mode |
| POST | `/api/sessions/{id}/documents` | Upload document |
| GET | `/api/sessions/{id}/documents` | List documents |
| GET | `/api/documents/{id}/status` | Get processing status |
| DELETE | `/api/documents/{id}` | Delete document |
| GET | `/api/sessions/{id}/messages` | Get chat history |
| DELETE | `/api/sessions/{id}/messages` | Clear history |
| POST | `/api/sessions/{id}/compact` | Force compaction |

### SSE Endpoint

```
POST /api/sessions/{id}/chat/stream
Content-Type: application/json
Accept: text/event-stream

Request:
{
  "message": "What is the main theme?",
  "mode": "RESEARCH"  // optional
}

SSE Events:
event: token
data: {"content": "The"}

event: token
data: {"content": " main"}

event: citation
data: {"source": "doc.pdf", "page": 1, "text": "..."}

event: done
data: {"messageId": "uuid", "promptTokens": 500, "completionTokens": 150}

event: error
data: {"errorId": "uuid", "message": "Service temporarily unavailable"}
```

---

## Testing Strategy

### Unit Tests (80% coverage target)
- Service layer: Mock repositories and external services
- Use Mockito for mocking
- Test naming: `should{Expected}_when{Condition}`

### Integration Tests
- Use Testcontainers for Elasticsearch
- Test full RAG pipeline
- Test SSE streaming

### Quality Checks
```bash
./gradlew spotlessApply    # Format code
./gradlew check            # All tests + quality
./gradlew jacocoTestReport # Coverage report
```

---

## Dependencies Summary

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 4.0.2 | Application framework |
| LangChain4j | 1.11.0 | LLM integration |
| Apache Tika | 3.2.3 | Document parsing |
| SQLite JDBC | 3.45.1.0 | Local database |
| Elasticsearch | 9.1.4 | Vector + BM25 search |
| TEI | cpu-1.9 (Docker) | Cross-encoder reranking (`BAAI/bge-reranker-base`) |
| Resilience4j | 2.2.0 | Circuit breakers |
| Micrometer | (managed) | Metrics |
| Testcontainers | 1.20.4 | Integration testing |
