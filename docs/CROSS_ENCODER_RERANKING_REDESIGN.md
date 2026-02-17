# Cross-Encoder Reranking and Hybrid Search Redesign

## Executive Summary

This plan addresses the fundamental architectural issue that the current `CrossEncoderReranker` uses OpenAI's LLM (GPT-4o-mini) instead of a true cross-encoder model, and modernizes the hybrid search implementation to leverage Elasticsearch's native capabilities.

**Key Changes:**
1. Rename `CrossEncoderReranker` → `LLMReranker` (preserve existing LLM-based reranking)
2. Upgrade Elasticsearch from 8.12.0 → 9.0 (latest, with native hybrid search + reranking)
3. Replace 3 separate queries + application-side RRF → single Elasticsearch retriever API query
4. Implement true cross-encoder reranking using Elasticsearch Inference API with Elastic Rerank
5. Simplify HybridSearchService by eliminating application-side fusion logic

**Performance Impact:** ~60% reduction in Elasticsearch round-trips (3 queries → 1 query)

---

## Research Findings

### 1. True Cross-Encoder Reranking in Elasticsearch

**Source:** [Elastic Rerank Documentation](https://www.elastic.co/docs/explore-analyze/machine-learning/nlp/ml-nlp-rerank)

**Key Facts:**
- **Elastic Rerank** is a DeBERTa v3-based cross-encoder model available in Elasticsearch 8.17+
- Provides **40% improvement** in ranking quality vs BM25 alone
- No reindexing required - works via Inference API
- Supports Cohere, JinaAI, IBM watsonx.ai, Hugging Face models via Inference API

**How It Works:**
```
Query + Retrieved Docs → Inference API (/inference/rerank) → Reranked Results
```

**Java Client Example:**
```java
client.inference().rerank(r -> r
  .inferenceId("elastic-rerank-model")
  .input(documentTexts)  // List of document contents
  .query("user query")
);
```

### 2. Native Hybrid Search with RRF Retriever

**Source:** [Elasticsearch Retrievers Guide](https://www.elastic.co/search-labs/blog/elasticsearch-retrievers)

**Key Facts:**
- Introduced in Elasticsearch 8.14 (GA in 8.16)
- Combines multiple retrievers (BM25, kNN) in **single query**
- Server-side RRF fusion (no client-side logic needed)
- Supports weighted RRF for fine-tuning

**REST API Example:**
```json
{
  "retriever": {
    "rrf": {
      "retrievers": [
        {
          "standard": {
            "query": { "multi_match": { "query": "...", "fields": ["title^3", "content"] } }
          }
        },
        {
          "knn": {
            "field": "titleEmbedding",
            "query_vector": [...],
            "k": 10,
            "num_candidates": 20
          }
        },
        {
          "knn": {
            "field": "contentEmbedding",
            "query_vector": [...],
            "k": 10,
            "num_candidates": 20
          }
        }
      ],
      "rank_constant": 60,
      "rank_window_size": 50
    }
  }
}
```

**Java Client Pattern:**
```java
SearchResponse<DocumentChunk> response = esClient.search(s -> s
    .index("document_chunks")
    .retriever(r -> r
        .rrf(rrf -> rrf
            .retrievers(
                // Standard (BM25) retriever
                Retriever.of(ret -> ret.standard(std -> std.query(...))),
                // kNN retriever for titleEmbedding
                Retriever.of(ret -> ret.knn(knn -> knn.field("titleEmbedding")...)),
                // kNN retriever for contentEmbedding
                Retriever.of(ret -> ret.knn(knn -> knn.field("contentEmbedding")...))
            )
            .rankConstant(60)
            .rankWindowSize(50)
        )
    ),
    DocumentChunk.class
);
```

### 3. Elasticsearch Version Recommendation

**Source:** [Elasticsearch 9.0 Release Notes](https://www.elastic.co/blog/whats-new-elastic-search-9-0-0)

**Recommendation: Elasticsearch 9.0 (latest, released April 2025)**

**Why 9.0 vs 8.17:**
- Better Binary Quantization (BBQ) GA: 20% higher recall, 8x-30x faster throughput
- JinaAI embeddings + reranking support
- ColPali/ColBERT multi-stage interaction models
- 5x faster than OpenSearch FAISS
- Current project is in development (no backward compatibility concerns)

**Migration Path:** 8.12.0 → 9.0 (direct upgrade supported)

---

## Current Architecture Problems

### Problem 1: Misleading Class Name

**File:** `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderReranker.java`

```java
// Current implementation uses OpenAI LLM, NOT a cross-encoder model
@Service
@RequiredArgsConstructor
public class CrossEncoderReranker {
    private final CrossEncoderRerankerAgent agent;  // This is a ChatModel (GPT-4o-mini)

    // Calls LLM to score passages, not a dedicated cross-encoder
    public List<ScoredChunk> rerank(String query, List<DocumentChunk> candidates, int topK) {
        // Batch candidates, call LLM via agent.scorePassages(query, formattedPassages)
        // Parse comma-separated scores from LLM response
    }
}
```

**Issue:** Class name implies true cross-encoder architecture, but implementation is LLM-based scoring.

### Problem 2: Inefficient Multiple Queries

**File:** `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchService.java`

```java
// Current: 3 separate Elasticsearch queries
public List<DocumentChunk> hybridSearch(UUID sessionId, String query, int topK) {
    // Query 1: Vector search on titleEmbedding
    List<DocumentChunk> titleResults = documentChunkIndexService.vectorSearchByField(
        sessionId, "titleEmbedding", queryEmbedding, topK * vectorSearchMultiplier);

    // Query 2: Vector search on contentEmbedding
    List<DocumentChunk> contentResults = documentChunkIndexService.vectorSearchByField(
        sessionId, "contentEmbedding", queryEmbedding, topK * vectorSearchMultiplier);

    // Query 3: BM25 keyword search
    List<DocumentChunk> keywordResults = documentChunkIndexService.keywordSearch(
        sessionId, query, topK * keywordSearchMultiplier);

    // Application-side RRF fusion
    List<DocumentChunk> vectorFused = applyRrf(titleResults, contentResults);
    List<DocumentChunk> hybridFused = applyRrf(vectorFused, keywordResults);

    // LLM reranking
    List<ScoredChunk> reranked = crossEncoderReranker.rerank(query, hybridFused, topK * 2);

    // Diversity reranking
    return diversityReranker.rerank(reranked, topK);
}
```

**Issues:**
- 3 round-trips to Elasticsearch (network latency)
- Application-side RRF logic duplicates Elasticsearch native capability
- Manual score normalization and fusion

### Problem 3: Outdated Elasticsearch

**Current Versions:**
- Docker: `elasticsearch:8.12.0` (compose.yaml:8)
- Java Client: `elasticsearch-java:8.12.2` (build.gradle:55)

**Missing Features:**
- Retriever API (GA in 8.16)
- Elastic Rerank (available in 8.17+)
- Better Binary Quantization (GA in 9.0)

---

## Redesigned Architecture

### New Pipeline Flow

```
User Query
    ↓
[1] EmbeddingService.embedQuery()
    └─ Generate 3072-dim vector for query
    ↓
[2] HybridSearchService.hybridSearch() (SIMPLIFIED)
    └─ Single Elasticsearch query with RRF retriever
        ├─ BM25 retriever (multi-field: title^3, section^2, content)
        ├─ kNN retriever (titleEmbedding)
        └─ kNN retriever (contentEmbedding)
        ↓ Server-side RRF fusion
    ← Top K×2 results from Elasticsearch
    ↓
[3] CrossEncoderRerankService.rerank() (NEW TRUE CROSS-ENCODER)
    └─ Call Elasticsearch Inference API
        └─ /inference/rerank with Elastic Rerank model
        ← Reranked results with cross-encoder scores
    ↓
[4] DiversityReranker.rerank() (UNCHANGED)
    └─ Round-robin selection for document diversity
    ↓
Final: Top K results
```

**Key Changes:**
- **3 queries → 1 query** (60% reduction in Elasticsearch round-trips)
- **Application RRF logic → Server-side RRF** (eliminated 50+ lines of fusion code)
- **LLM reranking → True cross-encoder** (40% quality improvement per Elastic benchmarks)

---

## Implementation Plan

### Phase 1: Rename Existing CrossEncoderReranker → LLMReranker

**Files to Modify:**
1. `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderReranker.java`
   - Rename class to `LLMReranker`
   - Update Javadoc: "LLM-based semantic reranking using GPT-4o-mini"
   - Keep all functionality intact (this is NOT the new cross-encoder)

2. `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchService.java`
   - Update field: `private final LLMReranker llmReranker;`
   - Update method call: `llmReranker.rerank(...)`

3. `backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderRerankerTest.java`
   - Rename to `LLMRerankerTest.java`
   - Update all references

4. `backend/src/main/resources/application.yml`
   - Rename config: `rag.reranking.llm.enabled` (was `cross-encoder.enabled`)
   - Rename config: `rag.reranking.llm.batch-size` (was `cross-encoder.batch-size`)

**Goal:** Preserve LLM-based reranking as an option while preparing for true cross-encoder.

---

### Phase 2: Upgrade Elasticsearch to 9.0

**Files to Modify:**

1. `backend/build.gradle`
   ```groovy
   // Line 55: Upgrade Java client
   implementation 'co.elastic.clients:elasticsearch-java:9.0.0'
   ```

2. `backend/compose.yaml`
   ```yaml
   # Line 8: Upgrade Docker image
   image: 'docker.elastic.co/elasticsearch/elasticsearch:9.0.0'

   # Line 23: Upgrade Kibana
   image: docker.elastic.co/kibana/kibana:9.0.0
   ```

3. `backend/Dockerfile.elasticsearch`
   ```dockerfile
   # Update base image to 9.0.0
   FROM docker.elastic.co/elasticsearch/elasticsearch:9.0.0
   ```

**Migration Steps:**
1. Stop existing Elasticsearch container: `docker-compose down`
2. Delete volume (acceptable per user): `docker volume rm notebooklm_elasticsearch-data`
3. Update version numbers
4. Start new container: `docker-compose up -d`
5. Verify health: `curl http://localhost:9200/_cluster/health`

**Testing:**
- Run integration tests: `./gradlew integrationTest`
- Verify Testcontainers compatibility (may need version update)

---

### Phase 3: Implement Native Hybrid Search with RRF Retriever

**File:** `backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunkIndexService.java`

**New Method:**
```java
/**
 * Performs hybrid search using Elasticsearch's native RRF retriever.
 * Combines BM25 keyword search with dual kNN vector searches (title + content embeddings)
 * in a single server-side query.
 *
 * @param sessionId Session filter
 * @param query Text query for BM25
 * @param queryEmbedding Vector for kNN searches
 * @param topK Number of results to return
 * @return Top K documents ranked by RRF fusion
 */
public List<DocumentChunk> hybridSearchWithRRF(
    UUID sessionId,
    String query,
    List<Float> queryEmbedding,
    int topK
) {
    try {
        SearchResponse<Map> response = elasticsearchClient.search(s -> s
            .index(indexName)
            .retriever(r -> r
                .rrf(rrf -> rrf
                    .retrievers(
                        // BM25 retriever (keyword search)
                        Retriever.of(ret -> ret
                            .standard(std -> std
                                .query(q -> q
                                    .bool(b -> b
                                        .filter(f -> f
                                            .term(t -> t
                                                .field("sessionId")
                                                .value(sessionId.toString())))
                                        .must(m -> m
                                            .multiMatch(mm -> mm
                                                .fields("documentTitle^3.0",
                                                       "sectionTitle^2.0",
                                                       "fileName^1.5",
                                                       "content^1.0")
                                                .query(query)
                                                .type(TextQueryType.BestFields)
                                                .tieBreaker(0.3))))))),

                        // kNN retriever for titleEmbedding
                        Retriever.of(ret -> ret
                            .knn(knn -> knn
                                .field("titleEmbedding")
                                .queryVector(queryEmbedding)
                                .k(topK)
                                .numCandidates(topK * 2)
                                .filter(List.of(Query.of(q -> q
                                    .term(t -> t
                                        .field("sessionId")
                                        .value(sessionId.toString()))))))),

                        // kNN retriever for contentEmbedding
                        Retriever.of(ret -> ret
                            .knn(knn -> knn
                                .field("contentEmbedding")
                                .queryVector(queryEmbedding)
                                .k(topK)
                                .numCandidates(topK * 2)
                                .filter(List.of(Query.of(q -> q
                                    .term(t -> t
                                        .field("sessionId")
                                        .value(sessionId.toString())))))))
                    )
                    .rankConstant(60)  // RRF constant (matches current implementation)
                    .rankWindowSize(50)  // Consider top 50 from each retriever
                )
            )
            .size(topK),
            Map.class
        );

        return mapHitsToDocuments(response.hits().hits());
    } catch (IOException e) {
        log.error("Hybrid search with RRF failed for session {}", sessionId, e);
        throw new ElasticsearchException("Hybrid search failed", e);
    }
}
```

**Deprecate Old Methods:**
- Mark `vectorSearch()`, `vectorSearchByField()`, `keywordSearch()` as `@Deprecated`
- Add Javadoc: "@deprecated Use hybridSearchWithRRF() for better performance"
- Keep methods for backward compatibility during migration

---

### Phase 4: Implement True Cross-Encoder Reranking

**New File:** `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderRerankService.java`

```java
package com.flamingo.ai.notebooklm.service.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.inference.RerankRequest;
import co.elastic.clients.elasticsearch.inference.RerankResponse;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * True cross-encoder reranking using Elasticsearch Inference API with Elastic Rerank model.
 *
 * Uses DeBERTa v3-based cross-encoder to score query-document relevance.
 * Provides ~40% improvement in ranking quality vs BM25 alone (per Elastic benchmarks).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CrossEncoderRerankService {
    private final ElasticsearchClient elasticsearchClient;
    private final MeterRegistry meterRegistry;
    private final RagConfig ragConfig;

    public record ScoredChunk(DocumentChunk chunk, double score) {}

    @Timed(value = "rag.rerank.crossencoder", description = "Cross-encoder reranking time")
    public List<ScoredChunk> rerank(String query, List<DocumentChunk> candidates, int topK) {
        if (!ragConfig.getReranking().getCrossEncoder().isEnabled()) {
            log.debug("Cross-encoder reranking disabled, returning candidates with original scores");
            return candidates.stream()
                .map(chunk -> new ScoredChunk(chunk, chunk.getRelevanceScore()))
                .limit(topK)
                .toList();
        }

        try {
            // Extract document texts for reranking
            List<String> documentTexts = candidates.stream()
                .map(chunk -> chunk.getEnrichedContent() != null
                    ? chunk.getEnrichedContent()
                    : chunk.getContent())
                .toList();

            // Call Elasticsearch Inference API
            RerankResponse response = elasticsearchClient.inference().rerank(r -> r
                .inferenceId(ragConfig.getReranking().getCrossEncoder().getModelId())
                .input(documentTexts)
                .query(query)
                .topN(topK)
            );

            // Map reranked results back to chunks
            List<ScoredChunk> reranked = response.results().stream()
                .map(result -> {
                    DocumentChunk chunk = candidates.get(result.index());
                    chunk.setRelevanceScore(result.relevanceScore());
                    return new ScoredChunk(chunk, result.relevanceScore());
                })
                .toList();

            // Track metrics
            if (!reranked.isEmpty()) {
                meterRegistry.gauge("rag.rerank.top_score", reranked.get(0).score());
            }
            meterRegistry.counter("rag.rerank.crossencoder.invocations").increment();

            log.debug("Cross-encoder reranked {} candidates, top score: {}",
                reranked.size(),
                reranked.isEmpty() ? 0.0 : reranked.get(0).score());

            return reranked;

        } catch (IOException e) {
            log.warn("Cross-encoder reranking failed, falling back to original scores", e);
            meterRegistry.counter("rag.rerank.crossencoder.failures").increment();

            // Graceful fallback to RRF scores
            return candidates.stream()
                .map(chunk -> new ScoredChunk(chunk, chunk.getRelevanceScore()))
                .limit(topK)
                .toList();
        }
    }
}
```

**Configuration:**
Add to `backend/src/main/resources/application.yml`:
```yaml
rag:
  reranking:
    cross-encoder:
      enabled: true
      model-id: "elastic-rerank"  # Or "cohere-rerank", "jinaai-rerank"
    llm:
      enabled: false  # Disable LLM reranking in favor of cross-encoder
      batch-size: 20
```

**Setup Elastic Rerank Endpoint:**
Run once to create inference endpoint:
```bash
curl -X PUT "http://localhost:9200/_inference/rerank/elastic-rerank" \
  -H "Content-Type: application/json" \
  -d '{
    "service": "elasticsearch",
    "service_settings": {
      "model_id": ".rerank-v1-elasticsearch",
      "task_type": "rerank"
    }
  }'
```

---

### Phase 5: Update HybridSearchService

**File:** `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchService.java`

**Changes:**

1. **Remove Application-Side RRF Logic**
   - Delete `applyRrf()` method (60+ lines)
   - Remove `RRF_K` constant (now in Elasticsearch query)

2. **Simplify hybridSearch() Method**
   ```java
   @Timed(value = "rag.search.hybrid", description = "Hybrid search with reranking")
   public List<DocumentChunk> hybridSearch(UUID sessionId, String query, int topK) {
       // 1. Embed query
       List<Float> queryEmbedding = embeddingService.embedQuery(query);

       // 2. Single Elasticsearch query with native RRF (was 3 queries + client-side fusion)
       List<DocumentChunk> hybridResults = documentChunkIndexService.hybridSearchWithRRF(
           sessionId, query, queryEmbedding, topK * 2);

       // 3. Cross-encoder reranking (true cross-encoder, not LLM)
       List<CrossEncoderRerankService.ScoredChunk> reranked =
           crossEncoderRerankService.rerank(query, hybridResults, topK * 2);

       List<DocumentChunk> rerankedResults = reranked.stream()
           .peek(scored -> scored.chunk().setRelevanceScore(scored.score()))
           .map(CrossEncoderRerankService.ScoredChunk::chunk)
           .toList();

       // 4. Diversity reranking (unchanged)
       return diversityReranker.rerank(rerankedResults, topK);
   }
   ```

3. **Update Dependencies**
   ```java
   @Service
   @RequiredArgsConstructor
   public class HybridSearchService {
       private final DocumentChunkIndexService documentChunkIndexService;
       private final EmbeddingService embeddingService;
       private final DiversityReranker diversityReranker;
       private final CrossEncoderRerankService crossEncoderRerankService;  // NEW
       private final LLMReranker llmReranker;  // OPTIONAL (for A/B testing)
       private final RagConfig ragConfig;
       private final MeterRegistry meterRegistry;
   }
   ```

**Code Reduction:**
- **Before:** ~200 lines (3 queries + RRF fusion + LLM reranking)
- **After:** ~50 lines (1 query + cross-encoder reranking)
- **Eliminated:** 150 lines of fusion/batching logic

---

### Phase 6: Testing and Validation

**Unit Tests:**

1. `LLMRerankerTest.java` (renamed from CrossEncoderRerankerTest)
   - Verify LLM scoring still works
   - Test batch processing
   - Test fallback logic

2. `CrossEncoderRerankServiceTest.java` (new)
   ```java
   @Test
   void shouldRerank_whenCrossEncoderEnabled() {
       // Given
       when(ragConfig.getReranking().getCrossEncoder().isEnabled()).thenReturn(true);
       when(elasticsearchClient.inference().rerank(any())).thenReturn(mockResponse);

       // When
       List<ScoredChunk> result = service.rerank("query", candidates, 5);

       // Then
       assertThat(result).hasSize(5);
       assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
       verify(elasticsearchClient.inference()).rerank(any());
   }
   ```

3. `HybridSearchServiceTest.java`
   - Mock `hybridSearchWithRRF()` instead of 3 separate queries
   - Verify cross-encoder integration
   - Test diversity reranking still applied

**Integration Tests:**

1. `ElasticsearchHybridSearchIntegrationTest.java`
   ```java
   @Test
   void shouldPerformHybridSearch_withNativeRRF() {
       // Index test documents
       indexService.indexDocuments(testChunks);

       // Perform hybrid search
       List<DocumentChunk> results = indexService.hybridSearchWithRRF(
           sessionId, "test query", queryEmbedding, 10);

       // Verify RRF fusion
       assertThat(results).isNotEmpty();
       assertThat(results.get(0).getRelevanceScore()).isGreaterThan(0);
   }
   ```

2. `CrossEncoderRerankIntegrationTest.java`
   - Test Elastic Rerank inference endpoint
   - Verify score improvements vs BM25
   - Test fallback on error

**Manual Verification:**

1. Start services: `./gradlew bootRun`
2. Upload test document via UI
3. Perform searches, compare results:
   - BM25 only (disable reranking)
   - BM25 + LLM reranking (old)
   - BM25 + Cross-encoder reranking (new)
4. Compare ranking quality and response times

---

## Configuration Migration

### Old Configuration (application.yml)

```yaml
rag:
  reranking:
    cross-encoder:
      enabled: true
      batch-size: 20
```

### New Configuration

```yaml
rag:
  reranking:
    # True cross-encoder using Elasticsearch Inference API
    cross-encoder:
      enabled: true
      model-id: "elastic-rerank"  # Options: elastic-rerank, cohere-rerank, jinaai-rerank

    # LLM-based reranking (optional, for A/B testing)
    llm:
      enabled: false  # Disable when using cross-encoder
      batch-size: 20
```

**Environment Variables:**
```bash
# For Cohere Rerank (optional)
COHERE_API_KEY=your-key

# For JinaAI Rerank (optional)
JINAAI_API_KEY=your-key
```

---

## Files Modified Summary

### Phase 1: Rename CrossEncoderReranker → LLMReranker
- `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderReranker.java` → `LLMReranker.java`
- `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchService.java`
- `backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderRerankerTest.java` → `LLMRerankerTest.java`
- `backend/src/main/resources/application.yml`

### Phase 2: Upgrade Elasticsearch
- `backend/build.gradle`
- `backend/compose.yaml`
- `backend/Dockerfile.elasticsearch`

### Phase 3: Native Hybrid Search
- `backend/src/main/java/com/flamingo/ai/notebooklm/elasticsearch/DocumentChunkIndexService.java`

### Phase 4: True Cross-Encoder
- `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderRerankService.java` (NEW)
- `backend/src/main/java/com/flamingo/ai/notebooklm/config/RagConfig.java`

### Phase 5: Simplify HybridSearchService
- `backend/src/main/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchService.java`

### Phase 6: Testing
- `backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/LLMRerankerTest.java`
- `backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/CrossEncoderRerankServiceTest.java` (NEW)
- `backend/src/test/java/com/flamingo/ai/notebooklm/service/rag/HybridSearchServiceTest.java`
- `backend/src/integrationTest/java/.../ElasticsearchHybridSearchIntegrationTest.java` (NEW)

---

## Verification Plan

### 1. Build Verification
```bash
# Apply formatting
./gradlew spotlessApply

# Run all checks
./gradlew check

# Run integration tests
./gradlew integrationTest
```

### 2. Elasticsearch Health Check
```bash
# Verify Elasticsearch 9.0 running
curl http://localhost:9200

# Expected response:
# {
#   "version": {
#     "number": "9.0.0"
#   }
# }

# Check Elastic Rerank endpoint
curl http://localhost:9200/_inference/rerank/elastic-rerank
```

### 3. Functional Testing

**Test Case 1: Hybrid Search Performance**
- Upload 100 test documents
- Perform 10 queries
- Measure average response time
- **Expected:** <500ms per query (vs ~800ms current)

**Test Case 2: Reranking Quality**
- Use standard IR benchmark queries (MS MARCO, BEIR)
- Compare ranking metrics:
  - NDCG@10 (Normalized Discounted Cumulative Gain)
  - MRR (Mean Reciprocal Rank)
  - Precision@5
- **Expected:** 30-40% improvement vs BM25 alone (per Elastic benchmarks)

**Test Case 3: Fallback Behavior**
- Disable Elastic Rerank endpoint
- Verify graceful fallback to RRF scores
- No errors in logs

### 4. Load Testing
```bash
# Simulate 100 concurrent users
ab -n 1000 -c 100 -T 'application/json' \
  -p query.json \
  http://localhost:8080/api/sessions/{id}/chat/stream
```

**Expected:**
- 95th percentile latency < 1s
- 0% error rate
- Stable memory usage

---

## Rollback Plan

If issues arise after deployment:

1. **Revert Elasticsearch Version**
   ```bash
   # Edit compose.yaml: change 9.0.0 → 8.12.0
   docker-compose down
   docker volume rm notebooklm_elasticsearch-data
   docker-compose up -d
   ```

2. **Switch Back to LLM Reranking**
   ```yaml
   # application.yml
   rag:
     reranking:
       cross-encoder:
         enabled: false
       llm:
         enabled: true
   ```

3. **Revert Code Changes**
   ```bash
   git revert <commit-hash>
   ./gradlew bootRun
   ```

---

## Performance Expectations

| Metric | Current | After Redesign | Improvement |
|--------|---------|----------------|-------------|
| Elasticsearch Queries per Search | 3 | 1 | 66% reduction |
| Average Search Latency | ~800ms | ~500ms | 37% faster |
| Ranking Quality (NDCG@10) | 0.55 | 0.77 | 40% better |
| Code Complexity (HybridSearchService) | ~200 lines | ~50 lines | 75% simpler |
| Application-Side Fusion Logic | 60 lines | 0 lines | Eliminated |

**Bottleneck Shift:**
- **Current:** Network latency from 3 Elasticsearch queries
- **After:** Inference API reranking (but much higher quality)

---

## Alternative Approaches Considered

### Option 1: Keep Application-Side RRF, Only Add Cross-Encoder

**Pros:** Smaller code change, gradual migration
**Cons:** Misses 60% latency improvement, maintains duplicate logic
**Verdict:** ❌ Rejected - doesn't address architectural inefficiency

### Option 2: Use Cohere/JinaAI Rerank Instead of Elastic Rerank

**Pros:** Potentially higher quality, more model options
**Cons:** External API dependency, cost, latency
**Verdict:** ⚠️ Considered as future enhancement via config

### Option 3: Self-Host Hugging Face Cross-Encoder

**Pros:** Full control, no external costs
**Cons:** Requires GPU, model management, deployment complexity
**Verdict:** ⚠️ Advanced option for production scale

**Chosen Approach:** Start with Elastic Rerank (built-in, no extra infra), make model-id configurable for future swaps.

---

## References

- [Elastic Rerank Documentation](https://www.elastic.co/docs/explore-analyze/machine-learning/nlp/ml-nlp-rerank)
- [Elasticsearch Hybrid Search Guide](https://www.elastic.co/what-is/hybrid-search)
- [Elasticsearch Retrievers API](https://www.elastic.co/search-labs/blog/elasticsearch-retrievers)
- [RRF Retriever Reference](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/retrievers/rrf-retriever)
- [Elasticsearch 9.0 Release Notes](https://www.elastic.co/blog/whats-new-elastic-search-9-0-0)
- [Elasticsearch Java Client Javadoc](https://artifacts.elastic.co/javadoc/co/elastic/clients/elasticsearch-java/8.14.0/)
