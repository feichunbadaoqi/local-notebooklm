# NotebookLM Clone

A localhost AI-powered knowledge assistant inspired by Google NotebookLM. Upload documents, ask questions, and get AI-generated answers with citations — all running on your own machine.

**Built entirely with [Claude Code](https://claude.ai/code)** — no code was written manually. This project serves as a learning exercise in using AI-assisted development to build a production-quality Hybrid RAG application.

## What It Does

- **Document Q&A**: Upload PDFs, Word docs, or text files and ask questions about their content
- **Hybrid RAG Search**: Combines vector similarity search with BM25 keyword search for better retrieval accuracy
- **Cross-Encoder Reranking**: Uses a TEI cross-encoder model to re-score search results for improved relevance
- **Multiple Interaction Modes**: Switch between Exploring (broad discovery), Research (precise citations), and Learning (Socratic method)
- **Chat Streaming**: Real-time token-by-token responses via Server-Sent Events
- **Session Management**: Organize conversations into separate sessions with their own documents
- **Chat History Compaction**: Automatically summarizes older messages to maintain context within token limits
- **Memory Extraction**: Extracts and remembers key facts from conversations for better follow-ups
- **Chinese Language Support**: Full CJK text support with IK Analysis plugin for Elasticsearch

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular 20 (SSR) + Tailwind CSS |
| Backend | Spring Boot 4.0.2 + LangChain4j 1.11.0 |
| LLM | OpenAI GPT (chat) + text-embedding-3-large (embeddings) |
| Search | Elasticsearch 9.1.4 (vector + BM25) + TEI cross-encoder reranker |
| Database | SQLite (sessions, chat messages, documents) |
| Document Parsing | Apache Tika 3.2.3 |
| Code Quality | Google Java Format, SpotBugs, Checkstyle, JaCoCo (80% coverage) |

## Architecture

```
┌──────────────────────────────────────────────────┐
│              Angular 20 Frontend                  │
│  Chat UI  |  Document Upload  |  Session Manager  │
└────────────────────┬─────────────────────────────┘
                     │ HTTP / SSE
┌────────────────────┴─────────────────────────────┐
│             Spring Boot Backend                    │
│                                                    │
│  REST API (/sessions, /documents)                  │
│  SSE Streaming (/chat/stream)                      │
│                                                    │
│  ┌──────────────────────────────────────────────┐ │
│  │ Hybrid RAG Pipeline                           │ │
│  │ Upload → Tika Parse → Chunk → Embed → Index   │ │
│  │ Query → Vector+BM25 → RRF Fusion → Rerank     │ │
│  └──────────────────────────────────────────────┘ │
│                                                    │
│  LangChain4j (Chat + Embeddings)                   │
│  Circuit Breakers + Retry (Resilience4j)           │
│  Metrics (Micrometer → Prometheus)                 │
└──────┬───────────────────────┬────────────────────┘
       │                       │
┌──────┴──────┐  ┌─────────────┴──────────┐
│   SQLite    │  │    Elasticsearch 9     │
│  Sessions   │  │  Vector Store (3072d)  │
│  Messages   │  │  BM25 Keyword Index    │
│  Documents  │  │  IK Chinese Analyzer   │
└─────────────┘  └────────────────────────┘
                          │
                 ┌────────┴────────┐
                 │  TEI Reranker   │
                 │  cross-encoder  │
                 │  ms-marco-      │
                 │  MiniLM-L6-v2   │
                 └─────────────────┘
```

## Prerequisites

- **Java 21+** (for Spring Boot 4)
- **Node.js 20+** and npm (for Angular 20)
- **Docker** and Docker Compose (for Elasticsearch, Kibana, TEI reranker)
- **OpenAI API Key** (for LLM chat and embeddings)

## Quick Start

### 1. Set your OpenAI API key

```bash
export OPENAI_API_KEY="your-openai-api-key"
```

On Windows (PowerShell):
```powershell
$env:OPENAI_API_KEY = "your-openai-api-key"
```

### 2. Start Docker services

The backend's `compose.yaml` defines three services:

| Service | Port | Purpose |
|---------|------|---------|
| Elasticsearch | 9200 | Vector + BM25 search index |
| Kibana | 5601 | ES visualization & debugging |
| TEI Reranker | 8090 | Cross-encoder reranking model |

```bash
cd backend
docker compose up -d
```

Wait for services to be healthy (Elasticsearch takes ~30s, TEI model download takes longer on first run):

```bash
# Check Elasticsearch
curl http://localhost:9200

# Check TEI reranker
curl http://localhost:8090/health
```

> **Note:** Spring Boot can auto-start Docker Compose on `bootRun` via its built-in Docker Compose support. If you prefer, just start the backend and it will bring up containers automatically.

### 3. Start the backend

```bash
cd backend
./gradlew bootRun
```

The backend starts on **http://localhost:8080**.

### 4. Start the frontend

```bash
cd frontend
npm install   # first time only
npm start
```

The frontend starts on **http://localhost:4200**.

### 5. Use the app

1. Open http://localhost:4200
2. Create a new session
3. Upload a document (PDF, Word, text, etc.)
4. Wait for processing (parsing, chunking, embedding)
5. Ask questions about your document

## How It Works

### Hybrid RAG Pipeline

When you upload a document:
1. **Parse**: Apache Tika extracts text from PDFs, Word docs, etc.
2. **Chunk**: Text is split into ~400-token chunks with 50-token overlap
3. **Embed**: Each chunk is embedded using OpenAI `text-embedding-3-large` (3072 dimensions)
4. **Index**: Chunks are stored in Elasticsearch with both vector embeddings and full text

When you ask a question:
1. **Embed query**: Your question is embedded using the same model
2. **Dual retrieval**: Elasticsearch runs both vector similarity search and BM25 keyword search
3. **RRF Fusion**: Results are merged using Reciprocal Rank Fusion: `score(d) = sum(1 / (60 + rank_i))`
4. **Rerank**: A cross-encoder model (TEI) re-scores the top candidates for better relevance
5. **Generate**: The top chunks become context for the LLM, which generates a cited answer
6. **Stream**: The response streams token-by-token via SSE to the chat UI

### Interaction Modes

| Mode | Behavior | Chunks Retrieved |
|------|----------|-----------------|
| Exploring | Broad discovery, suggests related topics | 8 |
| Research | Precise citations, fact-focused answers | 4 |
| Learning | Socratic method, explanatory answers | 6 |

### Resilience

- **Circuit Breakers**: OpenAI (30% failure threshold), Elasticsearch (50%), TEI (50%)
- **Retry**: Exponential backoff for transient failures
- **Graceful Degradation**: Search failures return empty results instead of errors

## API Endpoints

### REST

```
POST   /api/sessions                    Create session
GET    /api/sessions                    List sessions
GET    /api/sessions/{id}               Get session
PUT    /api/sessions/{id}/mode          Change interaction mode
DELETE /api/sessions/{id}               Delete session
POST   /api/sessions/{id}/documents     Upload document
GET    /api/sessions/{id}/messages      Get chat history
POST   /api/sessions/{id}/compact       Force history compaction
```

### SSE (Chat Streaming)

```
POST /api/sessions/{id}/chat/stream
Content-Type: application/json

{"message": "What is this document about?"}

Events: token, citation, done, error
```

## Development

### Backend

```bash
cd backend
./gradlew build                # Build + test + quality checks
./gradlew test                 # Unit tests only
./gradlew integrationTest      # Integration tests (needs Docker)
./gradlew check                # All checks: tests + SpotBugs + Checkstyle
./gradlew spotlessApply        # Auto-format (Google Java Style)
./gradlew jacocoTestReport     # Coverage report → build/reports/jacoco/
```

### Frontend

```bash
cd frontend
npm start                      # Dev server @ localhost:4200
npm run build                  # Production build
npm test                       # Karma/Jasmine tests
npm run lint                   # ESLint
npm run serve:ssr:frontend     # SSR production server
```

### Monitoring

- **Prometheus metrics**: http://localhost:8080/actuator/prometheus
- **Health check**: http://localhost:8080/actuator/health
- **Kibana** (ES debugging): http://localhost:5601

## Project Structure

```
notebooklm/
├── backend/                    # Spring Boot + LangChain4j
│   ├── src/main/java/          # Application code
│   ├── src/main/resources/     # application.yaml config
│   ├── src/integrationTest/    # Testcontainers integration tests
│   ├── compose.yaml            # Docker Compose (ES, Kibana, TEI)
│   ├── Dockerfile.elasticsearch # Custom ES with IK plugin
│   ├── build.gradle            # Gradle build + dependencies
│   └── data/                   # SQLite database files
│
├── frontend/                   # Angular 20 with SSR
│   ├── src/app/                # Angular components & services
│   ├── angular.json            # Angular CLI config
│   └── package.json            # npm dependencies
│
├── docs/                       # Design documents
│   └── DESIGN.md               # Consolidated architecture & design
│
├── CLAUDE.md                   # Root project instructions for Claude Code
└── README.md                   # This file
```

## Built with Claude Code

This entire project — backend, frontend, configuration, tests, documentation — was built using [Claude Code](https://claude.ai/code), Anthropic's CLI tool for AI-assisted development. The `CLAUDE.md` files in this repository serve as project memory, guiding Claude Code to maintain consistent architecture, coding standards, and quality across the codebase.

The purpose of this project is to explore how effectively an AI coding assistant can build a non-trivial, production-quality application from scratch, including:
- Hybrid RAG with multiple retrieval strategies
- Real-time streaming chat
- Resilience patterns (circuit breakers, retries)
- Full test coverage with quality gates
- Multi-language support

## License

This project is for educational and personal use.
