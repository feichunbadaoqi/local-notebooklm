# Output: Replace LLM Reranker with TEI Cross-Encoder Reranker

> **Date**: 2026-02-21
> **Status**: Completed
> **Build**: `./gradlew check spotlessCheck` — all tests + quality checks pass

## Summary

Replaced the LLM prompt-based reranker (`LLMReranker`) with a dedicated cross-encoder model served by Hugging Face TEI (Text Embeddings Inference). The new default reranker uses `BAAI/bge-reranker-base` running locally in Docker, providing ~10-60x faster reranking, zero API cost, and deterministic results.

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Interface name | `Reranker` | Follows project convention (Noun/Capability) |
| Bean selection | `@ConditionalOnProperty(name="rag.reranking.strategy")` | Matches existing `ImageGroupingStrategy` pattern |
| Default strategy | `tei` (`matchIfMissing=true`) | Fast, free, deterministic |
| HTTP client | `WebClient` (from webflux) | Already a dependency; `.timeout()` support |
| Reranker model | `BAAI/bge-reranker-base` | Originally planned `cross-encoder/ms-marco-MiniLM-L6-v2` but TEI only supports XLM-RoBERTa/CamemBERT/GTE/ModernBERT for re-ranking. `bge-reranker-base` is explicitly supported and well-regarded. |
| TEI fallback | Passthrough (return RRF scores as-is) | Avoids cascading to OpenAI; RRF results are already reasonable |
| TEI Docker port | `8090:80` | Avoids conflict with backend on `8080` |
| Docker image tag | `cpu-1.9` | Pinned stable version (not `cpu-latest`) for reproducibility |
| LLMReranker rename | `LlmPromptReranker` | Clarifies mechanism; vendor-neutral; marked `@Deprecated` |

## Model Compatibility Discovery

During implementation, we verified TEI's supported re-ranker architectures against the [official documentation](https://huggingface.co/docs/text-embeddings-inference/en/supported_models). The originally planned `cross-encoder/ms-marco-MiniLM-L6-v2` (BERT/MiniLM-based) is **NOT supported** by TEI for re-ranking. TEI only supports:

- **XLM-RoBERTa** (e.g., `BAAI/bge-reranker-base`, `BAAI/bge-reranker-large`)
- **CamemBERT**
- **GTE** (e.g., `Alibaba-NLP/gte-multilingual-reranker-base`)
- **ModernBERT** (e.g., `Alibaba-NLP/gte-reranker-modernbert-base`)

We switched to `BAAI/bge-reranker-base` — an XLM-RoBERTa model explicitly listed in TEI's supported models table, with strong multilingual reranking performance.

## Files Changed

### New Files (4)

| File | Purpose |
|------|---------|
| `service/rag/rerank/Reranker.java` | Interface with `rerank()` + nested `ScoredChunk` record |
| `service/rag/rerank/TeiCrossEncoderReranker.java` | Default impl calling TEI via `TeiRerankerClient` with circuit breaker + retry |
| `service/rag/rerank/TeiRerankerClient.java` | HTTP client encapsulating WebClient calls to TEI `/rerank` |
| `test/.../rerank/TeiCrossEncoderRerankerTest.java` | 6 unit tests for new reranker (scoring, sorting, topK, fallback) |

### Renamed Files (2)

| Old Name | New Name | Changes |
|----------|----------|---------|
| `LLMReranker.java` | `LlmPromptReranker.java` | Implements `Reranker`, `@Deprecated`, `@ConditionalOnProperty(havingValue="llm")` |
| `LLMRerankerTest.java` | `LlmPromptRerankerTest.java` | Updated class references + `ScoredChunk` references |

### Modified Files (5)

| File | Changes |
|------|---------|
| `HybridSearchService.java` | `LLMReranker` field → `Reranker` interface, updated `ScoredChunk` references |
| `RagConfig.java` | Replaced `CrossEncoder` inner class with `Tei` config; added `strategy` field |
| `application.yaml` | Added `rag.reranking.strategy: tei`, `rag.reranking.tei.*` config, `resilience4j.*.instances.tei` |
| `compose.yaml` | Added `tei-reranker` service + `tei-model-cache` volume |
| `HybridSearchServiceTest.java` | Changed mock from `LLMReranker` → `Reranker` |
| `SessionIsolationIntegrationTest.java` | Updated `LLMReranker` → `LlmPromptReranker` references |

### Updated Documentation (1)

| File | Changes |
|------|---------|
| `docs/IMPLEMENTATION_PLAN.md` | Added Reranking Strategy section, updated architecture diagram with TEI, updated dependencies table |

## Architecture

### Reranking Pipeline (before → after)

**Before:**
```
RRF Fusion → LLMReranker (OpenAI GPT prompt) → DiversityReranker
```

**After:**
```
RRF Fusion → Reranker (interface) → DiversityReranker
                ├── TeiCrossEncoderReranker (default, BAAI/bge-reranker-base via TEI)
                └── LlmPromptReranker (deprecated, OpenAI GPT prompt)
```

### TEI API Contract

```
POST http://localhost:8090/rerank
Content-Type: application/json

Request:  { "query": "...", "texts": ["...", "..."], "raw_scores": false, "truncate": true }
Response: [{ "index": 0, "score": 0.98 }, { "index": 1, "score": 0.02 }]
```

### Resilience Configuration

| Component | Circuit Breaker | Retry | Time Limiter |
|-----------|----------------|-------|--------------|
| TEI | 50% failure rate, 10s wait | 2 attempts, 500ms wait | 15s timeout |
| Fallback | Returns candidates with existing RRF scores (no cascading to OpenAI) |

### Docker Service

```yaml
tei-reranker:
  image: ghcr.io/huggingface/text-embeddings-inference:cpu-1.9
  container_name: notebooklm-tei-reranker
  command: --model-id BAAI/bge-reranker-base --port 80
  ports:
    - '8090:80'
  volumes:
    - tei-model-cache:/data
```

## Configuration

```yaml
rag:
  reranking:
    strategy: tei  # "tei" (default) or "llm"
    tei:
      base-url: http://localhost:8090
      model-id: BAAI/bge-reranker-base
      truncate: true
      raw-scores: false
      connect-timeout-ms: 5000
      read-timeout-ms: 10000
```

To switch back to LLM-based reranking: set `rag.reranking.strategy: llm` in `application.yaml`.

## Verification Steps

1. `./gradlew check spotlessCheck` — all tests + quality checks pass
2. Start TEI: `docker compose up tei-reranker` — verify model downloads and `/rerank` endpoint responds
3. Start full stack: `docker compose up` — verify all services (ES + Kibana + TEI) start together
4. Start backend: `./gradlew bootRun` — verify `TeiCrossEncoderReranker` bean is created (check startup logs)
5. Verify strategy switch: set `rag.reranking.strategy=llm` in yaml → verify `LlmPromptReranker` bean is created instead

## Metrics

| Metric | Description |
|--------|-------------|
| `rag.reranker.tei_seconds_*` | `@Timed` auto-generated: count, sum, max |
| `rag.reranker.tei.top_score` | Gauge: highest score from last reranking |
| `rag.reranker.tei.invocations` | Counter: total TEI reranking calls |
| `rag.reranker.tei.fallback` | Counter: times fallback was triggered |
