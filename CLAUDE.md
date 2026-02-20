# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 📁 Directory-Specific Guidance

**Claude Code automatically loads context based on your working directory:**

- **Backend work** (Java/Spring): See [`backend/CLAUDE.md`](backend/CLAUDE.md) for Java/Spring patterns, Elasticsearch, metrics, and Gradle commands
- **Frontend work** (Angular/TS): See [`frontend/CLAUDE.md`](frontend/CLAUDE.md) for Angular component patterns, RxJS, and npm commands
- **Root work** (git, docs): This file provides shared architecture and project overview

## Project Overview

**NotebookLM Clone** - A localhost AI-powered knowledge assistant with chat UI, document Q&A, and multiple interaction modes.

| Component | Technology |
|-----------|------------|
| Frontend | Angular 20 with SSR |
| Backend | Spring Boot 4.0.2 + LangChain4j |
| LLM | OpenAI GPT models (see application.yaml for model versions) |
| Storage | SQLite (sessions/chat) + Elasticsearch (vectors) |
| API | REST (CRUD) + SSE (chat streaming) |

**Detailed implementation plan:** See `docs/IMPLEMENTATION_PLAN.md`

## SOLID Principles

**CRITICAL: Apply SOLID principles consistently across backend and frontend code.**

| Principle | Backend (Java/Spring) | Frontend (TypeScript/Angular) |
|-----------|----------------------|------------------------------|
| **S**ingle Responsibility | One service = one business capability | One component/service = one purpose |
| **O**pen/Closed | Use interfaces + strategy pattern | Use abstract classes + composition |
| **L**iskov Substitution | Implementations must honor interface contracts | Derived classes must be substitutable |
| **I**nterface Segregation | Small, focused interfaces (not god interfaces) | Define minimal, role-specific interfaces |
| **D**ependency Inversion | Depend on abstractions (interfaces), not concrete classes | Inject abstractions via constructor |

**See stack-specific guidance:**
- **Backend**: [`backend/CLAUDE.md#interface-driven-design`](backend/CLAUDE.md#interface-driven-design)
- **Frontend**: [`frontend/CLAUDE.md#service-design`](frontend/CLAUDE.md#service-design)

## Layered Architecture

Follow strict layer separation:

```
┌─────────────────────────────────────┐
│  Presentation Layer (Controllers)   │  ← REST/SSE endpoints
├─────────────────────────────────────┤
│  Service Layer (Business Logic)     │  ← Orchestration, validation
├─────────────────────────────────────┤
│  Repository Layer (Data Access)     │  ← CRUD operations
├─────────────────────────────────────┤
│  Domain Layer (Entities, DTOs)      │  ← Data models
└─────────────────────────────────────┘
```

**Rules:**
- Controllers call Services (never Repositories directly)
- Services call Repositories and other Services
- Repositories only access database
- No business logic in Controllers or Repositories
- Use DTOs for API requests/responses (never expose Entities directly)

**For implementation examples:**
- **Backend (Java)**: See [`backend/CLAUDE.md#layered-architecture`](backend/CLAUDE.md#layered-architecture)
- **Frontend (Angular)**: See [`frontend/CLAUDE.md#component-design`](frontend/CLAUDE.md#component-design)

## Development Environment

**IMPORTANT: Claude Code CLI uses bash (Git Bash/WSL on Windows, native bash on Unix).**

### Shell Commands

- Use standard Unix/bash commands (`cat`, `rm`, `grep`, `find`)
- Chain commands with `&&` for sequential execution or `&` for background
- Use backslashes (`\`) for line continuation
- File paths: Use forward slashes (`/`) for cross-platform compatibility
- Windows paths work with forward slashes in bash (e.g., `backend/src/main`)

### Examples

```bash
# Standard bash commands
cat file.txt | head -10
sleep 5
rm -rf ./temp
./gradlew build && ./gradlew check  # Sequential execution

# File operations
rm "path/to/file.java"
mkdir -p backend/src/main/java
find . -name "*.java" -type f
```

## Common Commands

### Frontend (`frontend/` directory)

```bash
npm start                      # Dev server at localhost:4200
npm run build                  # Production build
npm test                       # Run Karma/Jasmine tests
npm run serve:ssr:frontend     # Run SSR production server
```

**For full frontend commands, see [`frontend/CLAUDE.md#npm-commands`](frontend/CLAUDE.md#npm-commands)**

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

**For full Gradle commands, see [`backend/CLAUDE.md#gradle-commands`](backend/CLAUDE.md#gradle-commands)**

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

**For implementation details, see [`backend/CLAUDE.md#elasticsearch-abstraction`](backend/CLAUDE.md#elasticsearch-abstraction)**

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

## Git Commit Strategy

**IMPORTANT: Commit code at each finished stage to preserve progress.**

### Commit at These Milestones

- After completing a logical feature/service (e.g., "Add RAG services")
- After fixing build/test issues
- Before starting a new major component
- When all quality checks pass

### Commit Message Format

```
<type>: <short description>

<optional body with details>

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

### Example Workflow

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

## Common Anti-Patterns to Avoid

| ❌ Anti-Pattern | ✅ Better Approach |
|----------------|-------------------|
| God classes (1000+ lines) | Split into focused classes by responsibility |
| Circular dependencies | Introduce interfaces, use events/mediator pattern |
| Magic numbers/strings | Define named constants |
| Catching generic `Exception` | Catch specific exceptions |
| Deep nesting (>3 levels) | Extract methods, use guard clauses |
| Copy-paste code | Extract shared logic to utilities/base classes |

**For stack-specific anti-patterns:**
- **Backend (Java)**: See [`backend/CLAUDE.md#common-anti-patterns-to-avoid`](backend/CLAUDE.md#common-anti-patterns-to-avoid)
- **Frontend (Angular)**: See [`frontend/CLAUDE.md#common-anti-patterns-to-avoid`](frontend/CLAUDE.md#common-anti-patterns-to-avoid)

## Documentation Guidelines

**When to Add Comments:**
- Complex algorithms or business logic
- Non-obvious workarounds or hacks
- Public API methods (use Javadoc/TSDoc)

**When NOT to Add Comments:**
- Self-explanatory code (prefer better naming)
- Redundant descriptions (e.g., `// Set name` above `setName()`)

## Stack-Specific Guidance

For detailed coding standards, testing strategies, and tooling commands:

- **Backend (Java/Spring)**: See [`backend/CLAUDE.md`](backend/CLAUDE.md)
  - Interface-Driven Design
  - Layered Architecture
  - Metrics & Observability
  - Elasticsearch Abstraction
  - Resilience Patterns
  - Testing (JUnit 5 + Mockito + Testcontainers)
  - Gradle Commands

- **Frontend (Angular/TS)**: See [`frontend/CLAUDE.md`](frontend/CLAUDE.md)
  - Component Design (Smart vs Presentational)
  - RxJS Best Practices
  - Dependency Injection
  - Error Handling
  - Performance Optimization
  - Testing (Karma/Jasmine)
  - npm Commands
