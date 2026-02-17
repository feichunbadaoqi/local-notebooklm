# Backend Coding Standards (Java/Spring Boot)

**This file is automatically loaded by Claude Code when working in `backend/`.**

## Project Context

**For shared project architecture, API endpoints, and SOLID principles, see [root CLAUDE.md](../CLAUDE.md).**

This file contains Java/Spring Boot specific coding standards for the NotebookLM backend.

**Tech Stack:** Spring Boot 4.0.2 · LangChain4j 1.11.0 · SQLite (JPA/Hibernate) · Elasticsearch 9 · OpenAI GPT-4o-mini

## Interface-Driven Design

**MANDATORY: All service and repository layers MUST define interfaces.**

- **Repository Layer**: Extend `JpaRepository<Entity, ID>` — Spring Data auto-generates implementation
- **Service Layer**: Define interface (e.g., `SessionService`) + implementation (`SessionServiceImpl implements SessionService`)
- **Why**: Testability (mock interfaces), flexibility (swap impls), Spring AOP/transaction support
- **Exceptions**: Utility classes, DTOs, entities, configuration classes

## Layered Architecture

**CRITICAL: Controllers MUST NOT inject or access repositories directly.**

- Controllers → Services only (never Repositories)
- Services → Repositories and other Services
- Repositories → database only
- Business logic (validation, state transitions, aggregations) belongs in services
- Use DTOs for all API request/response objects — never expose entities directly

**Anti-pattern**: Controller injecting `DocumentRepository` or `ChatMessageRepository` directly and calling query methods on them.

**Correct**: Add a service method (e.g., `getSessionWithStats(UUID)`) that aggregates data and returns a DTO — controller calls only that.

## Dependency Injection

**Use constructor injection with Lombok `@RequiredArgsConstructor`.** Never use `@Autowired` field injection — it's mutable and harder to test.

## Error Handling

- Throw specific exceptions (e.g., `ResourceNotFoundException(resourceType, id)`) — never generic `Exception`
- Use `@RestControllerAdvice` + `@ExceptionHandler` in a `GlobalExceptionHandler` for uniform error responses
- Return structured `ApiError` with message, code, and UUID `errorId` for log correlation

## Naming Conventions

| Element | Pattern | Example |
|---------|---------|---------|
| Interface | Noun/Capability | `SessionService`, `DocumentRepository` |
| Implementation | Interface + "Impl" | `SessionServiceImpl`, `ChatServiceImpl` |
| REST Controller | Resource + "Controller" | `SessionController`, `ChatController` |
| DTO | Purpose + "Request"/"Response"/"Dto" | `CreateSessionRequest`, `SessionDto` |
| Entity | Domain noun | `Session`, `ChatMessage`, `Document` |
| Test | Method + "_when" + Condition | `shouldCreateSession_whenValidRequest()` |
| Constants | UPPER_SNAKE_CASE | `MAX_CHUNK_SIZE`, `DEFAULT_EMBEDDING_MODEL` |

## Package Structure

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

All metrics exported via Micrometer to `/actuator/prometheus`.

| Layer | Approach |
|-------|----------|
| Controllers | **Built-in** `http.server.requests` — do NOT add `@Timed` |
| Services | **`@Timed`** annotation — auto-provides count, sum, max, exception tags |
| Business events | **Manual** `meterRegistry.counter()` / `Gauge` for tokens, uploads, extractions |

**Rule**: Use `@Timed` for all service-layer timing. Never use manual `Timer.Sample` (verbose, no exception tags).

**`@Timed` auto-generates**: `{name}_seconds_count`, `{name}_seconds_sum`, `{name}_seconds_max` with `exception` tag.

## Elasticsearch Abstraction

**CRITICAL: Implement `ElasticsearchIndexOperations<T, ID>` for all new index services.**

- **Interface**: `ElasticsearchIndexOperations<T, ID>` — type-safe generic operations (initIndex, indexDocuments, vectorSearch, keywordSearch, deleteBy)
- **Delegate**: Inject `ElasticsearchIndexService` as the concrete delegate
- **Pattern**: Add domain-specific convenience methods on top (e.g., `indexChunks()`, `deleteByDocumentId()`)
- **Current impl**: `DocumentChunkIndexService implements ElasticsearchIndexOperations<DocumentChunk, String>`
- **New index**: Create a new `*IndexService` implementing the interface — do not modify `DocumentChunkIndexService`

## Resilience Patterns

- **Circuit Breakers**: OpenAI (30% failure threshold), Elasticsearch (50%)
- **Retry**: OpenAI rate limits — 3 attempts, exponential backoff
- **Graceful Degradation**: Return empty results if search fails; never propagate Elasticsearch errors to users

## Testing

- **Coverage**: 80% minimum (JaCoCo enforced)
- **Unit Tests**: JUnit 5 + Mockito — test behavior (`should{Expected}_when{Condition}`), not internal implementation details
- **Integration Tests**: Testcontainers (Elasticsearch)
- **Pattern**: Given/When/Then — mock collaborators, assert return values, use `verify()` only for meaningful interactions

## Code Quality

- **Format**: Google Java Style — run `./gradlew spotlessApply` before committing
- **Static Analysis**: SpotBugs + Checkstyle
- **All checks must pass** before every commit: `./gradlew check spotlessCheck`

## Performance

- Use `@Transactional` for multi-operation DB workflows
- Fetch collections lazily; use pagination for large result sets
- Cache expensive computations with `@Cacheable`; use `@Async` for long-running tasks

## Security

- Validate all user inputs with `@Valid` + Bean Validation
- Sanitize file uploads (check MIME types, enforce size limits)
- Never log sensitive data (passwords, tokens, PII)
- Spring Data JPA parameterized queries prevent SQL injection by default

## Common Anti-Patterns to Avoid

| ❌ Anti-Pattern | ✅ Better Approach |
|----------------|-------------------|
| God classes (1000+ lines) | Split into focused classes by responsibility |
| Circular dependencies | Introduce interfaces, use events/mediator pattern |
| Magic numbers/strings | Define named constants |
| Catching generic `Exception` | Catch specific exceptions |
| Returning `null` | Return `Optional<T>` |
| Mutable DTOs | Use immutable records |
| Deep nesting (>3 levels) | Extract methods, use guard clauses |
| Copy-paste code | Extract shared logic to utilities/base classes |

## Dependency Management

Always use the latest stable versions (no beta/alpha/RC). Check [Maven Central](https://central.sonatype.com/) before adding or updating.

**Key dependencies**: LangChain4j 1.11.0 · Apache Tika 3.2.3 · Testcontainers 1.20.4 · Elasticsearch Client 8.12.2

## Development Workflow

1. Make code changes
2. `./gradlew spotlessApply` — auto-format
3. `./gradlew check` — tests + quality checks
4. Review coverage at `build/reports/jacoco/test/html/index.html`
5. Commit only when all checks pass

## Gradle Commands

```bash
./gradlew bootRun              # Start Spring Boot server
./gradlew build                # Build + test + quality checks
./gradlew test                 # Run unit tests
./gradlew integrationTest      # Run integration tests
./gradlew check                # All checks (test, spotbugs, checkstyle)
./gradlew spotlessApply        # Apply Google Java Format
./gradlew jacocoTestReport     # Generate coverage report (target: 80%)

# After every code change:
./gradlew check spotlessCheck
```
