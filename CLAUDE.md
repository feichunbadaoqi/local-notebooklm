# CLAUDE.md

## Project Overview

**NotebookLM Clone** — localhost AI knowledge assistant with document Q&A, hybrid RAG, and chat streaming.

| Component | Technology |
|-----------|-----------|
| Frontend | Angular 20 (SSR) + Tailwind CSS |
| Backend | Spring Boot 4.0.2 + LangChain4j 1.11.0 |
| LLM | OpenAI GPT (chat) + text-embedding-3-large (embeddings) |
| Storage | SQLite (sessions/chat) + Elasticsearch 9.1.4 (vectors + BM25) |
| API | REST (CRUD) + SSE (chat streaming) |

Stack-specific guidance: `backend/CLAUDE.md` (Java/Spring), `frontend/CLAUDE.md` (Angular/TS).

## Architecture Rules

**Layered architecture — strict separation:**
- Controllers → Services only (never Repositories)
- Services → Repositories + other Services
- No business logic in Controllers or Repositories
- DTOs for all API request/response (never expose entities)

**SOLID principles — apply consistently:**
- Single Responsibility: one class = one purpose
- Interface-driven: services define interface + `*Impl` class
- Depend on abstractions, not concrete classes

## Hybrid RAG Pipeline

1. **Ingest**: Upload → Tika Parse → Chunk (400 tokens, 50 overlap) → Embed (text-embedding-3-large, 3072d) → Index in ES
2. **Query**: Embed query → Vector Search + BM25 → RRF Fusion (`score = Σ 1/(60 + rank_i)`) → TEI Cross-Encoder Rerank → Context
3. **Modes**: EXPLORING (8 chunks), RESEARCH (4 chunks), LEARNING (6 chunks)

## Data Models (SQLite)

- **Session**: id, title, currentMode, createdAt, updatedAt
- **Document**: id, sessionId, fileName, mimeType, status, chunkCount
- **ChatMessage**: id, sessionId, role, content, tokenCount, isCompacted
- **ChatSummary**: id, sessionId, summaryContent, messageCount
- **Memory**: id, sessionId, memoryContent, memoryType, importance

## API Endpoints

```
POST   /api/sessions                    Create session
GET    /api/sessions                    List sessions
GET    /api/sessions/{id}               Get session
PUT    /api/sessions/{id}/mode          Change mode
DELETE /api/sessions/{id}               Delete session
POST   /api/sessions/{id}/documents     Upload document
GET    /api/sessions/{id}/messages      Get chat history
POST   /api/sessions/{id}/compact       Force compaction
POST   /api/sessions/{id}/chat/stream   SSE chat (events: token, citation, done, error)
```

## Quick Commands

```bash
# Backend (from backend/)
./gradlew bootRun                      # Start server (auto-starts Docker services)
./gradlew check spotlessCheck          # All quality checks — run after every change
./gradlew spotlessApply                # Auto-format code

# Frontend (from frontend/)
npm start                              # Dev server @ localhost:4200
npm run build                          # Production build
npm test                               # Run tests
```

## Commit Convention

Format: `<type>: <description>` — types: feat, fix, refactor, test, docs, chore.
Run `./gradlew check spotlessCheck` before every commit. Commit at each completed logical unit.

## Code Quality Rules

- No god classes (>500 lines) — split by responsibility
- No magic numbers/strings — use named constants
- No generic `Exception` catches — catch specific exceptions
- No deep nesting (>3 levels) — extract methods, use guard clauses
- No copy-paste code — extract shared logic
- Comments only for non-obvious "why", never for obvious "what"
