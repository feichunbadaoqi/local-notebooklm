# Backend Standards (Java/Spring Boot)

**Tech:** Spring Boot 4.0.2 · LangChain4j 1.11.0 · SQLite (JPA) · Elasticsearch 9 · OpenAI GPT

## Mandatory Patterns

- **Interface-driven services**: Define interface (`SessionService`) + implementation (`SessionServiceImpl`). Exceptions: utilities, DTOs, entities, config classes.
- **Constructor injection**: Use Lombok `@RequiredArgsConstructor`. Never use `@Autowired` field injection.
- **DTOs for all APIs**: Never expose JPA entities in controllers. Use records for immutable DTOs.
- **Structured errors**: Throw specific exceptions (e.g., `ResourceNotFoundException`). Global handler via `@RestControllerAdvice` returns `ApiError` with errorId for log correlation.

## Naming

| Element | Pattern | Example |
|---------|---------|---------|
| Interface | Noun | `SessionService` |
| Implementation | Interface + `Impl` | `SessionServiceImpl` |
| Controller | Resource + `Controller` | `SessionController` |
| DTO | Purpose + `Request`/`Response`/`Dto` | `CreateSessionRequest` |
| Test method | `should{Expected}_when{Condition}` | `shouldCreateSession_whenValidRequest()` |

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
│   ├── rag/          # Embedding, search, RRF fusion, reranking
│   └── memory/       # Memory extraction
├── api/
│   ├── rest/         # REST controllers
│   ├── sse/          # SSE controllers
│   └── dto/          # Request/Response objects
├── elasticsearch/    # ES client, index, queries
└── exception/        # Global error handling
```

## Elasticsearch Abstraction

New index services MUST implement `ElasticsearchIndexOperations<T, ID>` and delegate to `ElasticsearchIndexService`. Add domain-specific methods on top. Do not modify existing `DocumentChunkIndexService`.

## Metrics

| Layer | Approach |
|-------|----------|
| Controllers | Built-in `http.server.requests` — do NOT add `@Timed` |
| Services | `@Timed` annotation (auto: count, sum, max, exception tags) |
| Business events | Manual `meterRegistry.counter()` / `Gauge` |

Never use manual `Timer.Sample` — always `@Timed` for services.

## Resilience

- Circuit Breakers: OpenAI (30%), Elasticsearch (50%), TEI (50%)
- Retry: OpenAI 3 attempts exponential backoff, ES 3/500ms, TEI 2/500ms
- Graceful degradation: return empty results on search failure, never propagate ES errors to users

## Testing

- **80% coverage minimum** (JaCoCo enforced)
- Unit: JUnit 5 + Mockito — Given/When/Then, test behavior not internals
- Integration: Testcontainers (Elasticsearch)
- Use `verify()` only for meaningful interaction assertions

## Quality Checks

```bash
./gradlew spotlessApply        # Google Java Format
./gradlew check spotlessCheck  # Tests + SpotBugs + Checkstyle + format — run after every change
```

## Key Dependencies

LangChain4j 1.11.0 · Apache Tika 3.2.3 · Testcontainers 1.20.4 · Elasticsearch Client 9.0.0. Always use latest stable (no beta/RC).
