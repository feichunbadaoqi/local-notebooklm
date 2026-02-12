# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**NotebookLM Clone** - A localhost AI-powered knowledge assistant with chat UI, document Q&A, and multiple interaction modes.

| Component | Technology |
|-----------|------------|
| Frontend | Angular 20 with SSR |
| Backend | Spring Boot 4.0.2 + LangChain4j |
| LLM | OpenAI GPT-4o-mini + text-embedding-3-small |
| Storage | SQLite (sessions/chat) + Elasticsearch (vectors) |
| API | REST (CRUD) + SSE (chat streaming) |

**Detailed implementation plan:** See `docs/IMPLEMENTATION_PLAN.md`

## Development Environment

**IMPORTANT: This project is developed on Windows with PowerShell.**

### Shell Commands
- Always use PowerShell-compatible syntax
- Use `Get-Content` instead of `cat`
- Use `Remove-Item` instead of `rm`
- Use semicolons (`;`) for command chaining, not `&&`
- Use backticks (`` ` ``) for line continuation, not backslashes (`\`)
- File paths: Use backslashes or forward slashes (both work in PowerShell)
- Use `powershell.exe` or `pwsh` for running .ps1 scripts, not `bash`

### Time/Sleep Commands
- Use `Start-Sleep -Seconds 30` instead of `sleep 30`
- For timeouts in scripts, use PowerShell's timeout mechanisms

### Examples:
```powershell
# PowerShell (CORRECT for this project)
Get-Content file.txt | Select-Object -First 10
Start-Sleep -Seconds 5
Remove-Item -Recurse -Force ./temp
powershell.exe -File script.ps1

# Bash (WRONG for this project)
cat file.txt | head -10
sleep 5
rm -rf ./temp
bash script.sh
```

## Common Commands

### Frontend (`frontend/` directory)
```bash
npm start                      # Dev server at localhost:4200
npm run build                  # Production build
npm test                       # Run Karma/Jasmine tests
npm run serve:ssr:frontend     # Run SSR production server
```

### Backend (`backend/` directory)
```bash
./gradlew bootRun              # Start Spring Boot server
./gradlew build                # Build + test + quality checks
./gradlew test                 # Run unit tests
./gradlew integrationTest      # Run integration tests
./gradlew check                # All checks (test, spotbugs, checkstyle)
./gradlew spotlessApply        # Apply Google Java Format
./gradlew jacocoTestReport     # Generate coverage report (target: 80%)
```

**After every backend code change:**
```bash
./gradlew check spotlessCheck  # Tests + FindBugs + style + format
```

## Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                   Angular 20 Frontend                        │
│  Chat UI │ Document Upload │ Mode Selector │ Session Mgmt   │
└─────────────────────────────────┬───────────────────────────┘
                                  │ HTTP/SSE
┌─────────────────────────────────┴───────────────────────────┐
│                 Spring Boot Backend                          │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ REST API: /sessions, /documents   SSE: /chat/stream    │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Services: Session │ Document │ Chat │ RAG │ Memory     │ │
│  │ Resilience: Circuit Breakers (OpenAI, Elasticsearch)   │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ LangChain4j: ChatModel │ EmbeddingModel │ Retriever    │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Hybrid RAG: Tika Parse → Chunk → Embed → Vector+BM25   │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────┬─────────────────────┬────────────────────┘
                   │                     │
           ┌───────┴───────┐     ┌───────┴───────┐
           │    SQLite     │     │ Elasticsearch │
           │  Sessions     │     │  Vectors      │
           │  ChatMessages │     │  BM25 Index   │
           │  Documents    │     │               │
           └───────────────┘     └───────────────┘
```

### Why LangChain4j (not LangGraph4j)

LangChain4j is sufficient for our RAG + chat use case. LangGraph4j adds complexity for multi-agent workflows we don't need yet. We can add it later if we need:
- Autonomous multi-step research agents
- Complex retry/fallback logic between LLMs
- Collaborative multi-agent features

### Hybrid RAG Pipeline

1. **Document Ingestion**: Upload → Tika Parse → Chunk (512 tokens, 50 overlap) → Embed → Index
2. **Query Processing**: Query → Embed → Vector Search + BM25 → RRF Fusion → Context
3. **RRF Formula**: `score(doc) = Σ 1/(60 + rank_i)` for each retriever

### Interaction Modes (via System Prompts)

| Mode | Behavior | Retrieval |
|------|----------|-----------|
| EXPLORING | Broad discovery, suggestions | 8 chunks |
| RESEARCH | Precise citations, fact-focused | 4 chunks |
| LEARNING | Socratic method, explanations | 6 chunks |

### Chat History Compaction

- **Sliding window**: Keep last 10 messages in full
- **Trigger**: Total tokens > 3000 or messages > 30
- **Action**: Summarize older messages, store in ChatSummary table

### Data Models (SQLite)

- **Session**: id, title, currentMode, createdAt, updatedAt
- **Document**: id, sessionId, fileName, mimeType, status, chunkCount
- **ChatMessage**: id, sessionId, role, content, tokenCount, isCompacted
- **ChatSummary**: id, sessionId, summaryContent, messageCount
- **Memory**: id, sessionId, memoryContent, memoryType, importance

## API Endpoints

### REST (CRUD Operations)
```
POST   /api/sessions                    Create session
GET    /api/sessions                    List sessions
GET    /api/sessions/{id}               Get session
PUT    /api/sessions/{id}/mode          Change mode
DELETE /api/sessions/{id}               Delete session
POST   /api/sessions/{id}/documents     Upload document
GET    /api/sessions/{id}/messages      Get chat history
POST   /api/sessions/{id}/compact       Force compaction
```

### SSE (Chat Streaming)
```
POST /api/sessions/{id}/chat/stream

Events: token, citation, done, error
```

## Backend Package Structure

```
com.flamingo.ai.notebooklm/
├── config/           # Spring configs, Resilience4j, Metrics
├── domain/
│   ├── entity/       # JPA entities
│   ├── enums/        # InteractionMode, MessageRole, DocumentStatus
│   └── repository/   # Spring Data repositories
├── service/
│   ├── session/      # Session management
│   ├── document/     # Upload, parse, chunk
│   ├── chat/         # Chat, compaction, streaming
│   ├── rag/          # Embedding, search, RRF fusion
│   └── memory/       # Memory extraction
├── api/
│   ├── rest/         # REST controllers
│   ├── sse/          # SSE controllers
│   └── dto/          # Request/Response objects
├── elasticsearch/    # ES client, index, queries
└── exception/        # Global error handling
```

## Metrics & Observability

All metrics exported via Micrometer to `/actuator/prometheus`:

| Category | Key Metrics |
|----------|-------------|
| API | `api_request_duration_seconds`, `api_errors_total` |
| LLM | `llm_request_duration_seconds`, `llm_tokens_used_total` |
| RAG | `rag_retrieval_duration_seconds`, `rag_documents_retrieved` |
| SSE | `sse_connections_active`, `sse_events_sent_total` |

## Resilience

- **Circuit Breakers**: OpenAI (30% failure threshold), Elasticsearch (50%)
- **Retry**: OpenAI rate limits (3 attempts, exponential backoff)
- **Graceful Degradation**: Return empty results if search fails
- **Error Responses**: Structured `ApiError` with errorId for log correlation

## Testing Requirements

- **Coverage**: 80% unit test coverage (JaCoCo enforced)
- **Unit Tests**: JUnit 5 + Mockito
- **Integration Tests**: Testcontainers (Elasticsearch)
- **Naming**: `should{Expected}_when{Condition}`

## Code Quality

- **Format**: Google Java Style (Spotless)
- **Static Analysis**: SpotBugs
- **Style Check**: Checkstyle
- **All checks must pass before committing**

## Dependency Management

**IMPORTANT: Always use the latest stable versions of dependencies.**

### Before Adding/Updating Dependencies:
1. Check [Maven Central](https://central.sonatype.com/) or [MVN Repository](https://mvnrepository.com/) for the latest stable version
2. Avoid beta/alpha/RC versions unless specifically required
3. Update the version in `build.gradle` ext block for easy version management

### Current Key Dependencies (keep updated):
| Dependency | Version | Check Latest |
|------------|---------|--------------|
| LangChain4j | 1.11.0 | [Maven Central](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j) |
| Apache Tika | 3.2.3 | [Maven Central](https://central.sonatype.com/artifact/org.apache.tika/tika-core) |
| Testcontainers | 1.20.4 | [Maven Central](https://central.sonatype.com/artifact/org.testcontainers/testcontainers) |
| Elasticsearch Client | 8.12.2 | [Maven Central](https://central.sonatype.com/artifact/co.elastic.clients/elasticsearch-java) |

### When to Update:
- Before starting new features that depend on library functionality
- When encountering bugs that may be fixed in newer versions
- During periodic maintenance reviews

## Development Workflow

1. Make code changes
2. Run `./gradlew spotlessApply` to auto-format
3. Run `./gradlew check` to verify tests + quality
4. Review coverage at `build/reports/jacoco/test/html/index.html`
5. Commit only when all checks pass

## Git Commit Strategy

**IMPORTANT: Commit code at each finished stage to preserve progress.**

### Commit at These Milestones:
- After completing a logical feature/service (e.g., "Add RAG services")
- After fixing build/test issues
- Before starting a new major component
- When all quality checks pass

### Commit Message Format:
```
<type>: <short description>

<optional body with details>

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

### Example Workflow:
```bash
# After completing RAG services
./gradlew spotlessApply check
git add -A
git commit -m "feat: add RAG services with hybrid search

- EmbeddingService for OpenAI embeddings
- HybridSearchService with RRF fusion
- DocumentProcessingService with Tika parsing

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```
