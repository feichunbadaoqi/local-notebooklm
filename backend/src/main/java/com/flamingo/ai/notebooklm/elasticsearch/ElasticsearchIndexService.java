package com.flamingo.ai.notebooklm.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Service for managing document chunks in Elasticsearch with vector and keyword search. */
@Service
@Slf4j
public class ElasticsearchIndexService {

  private final ElasticsearchClient elasticsearchClient;
  private final MeterRegistry meterRegistry;

  @Value("${app.elasticsearch.index-name:notebooklm-chunks}")
  private String indexName;

  @Value("${app.elasticsearch.vector-dimensions:3072}")
  private int vectorDimensions;

  @Value("${app.elasticsearch.text-analyzer:standard}")
  private String textAnalyzer;

  /** Production constructor for Spring autowiring. */
  @org.springframework.beans.factory.annotation.Autowired
  public ElasticsearchIndexService(
      ElasticsearchClient elasticsearchClient, MeterRegistry meterRegistry) {
    this.elasticsearchClient = elasticsearchClient;
    this.meterRegistry = meterRegistry;
  }

  /** Constructor for testing - allows setting index name and vector dimensions. */
  public ElasticsearchIndexService(
      ElasticsearchClient elasticsearchClient,
      MeterRegistry meterRegistry,
      String indexName,
      int vectorDimensions) {
    this.elasticsearchClient = elasticsearchClient;
    this.meterRegistry = meterRegistry;
    this.indexName = indexName;
    this.vectorDimensions = vectorDimensions;
    this.textAnalyzer = "standard"; // Default for tests
  }

  @PostConstruct
  public void initIndex() {
    try {
      var indices = elasticsearchClient.indices();
      if (indices == null) {
        log.warn("Elasticsearch client not available, skipping index initialization");
        return;
      }
      boolean exists = indices.exists(e -> e.index(indexName)).value();
      if (!exists) {
        createIndex();
        log.info("Created Elasticsearch index: {}", indexName);
      }
    } catch (Exception e) {
      log.warn("Could not check/create Elasticsearch index: {}", e.getMessage());
    }
  }

  private void createIndex() throws IOException {
    log.info("Creating index '{}' with text analyzer: {}", indexName, textAnalyzer);
    if (!"standard".equals(textAnalyzer) && !"smartcn".equals(textAnalyzer)) {
      log.warn("Using custom analyzer '{}'. Ensure it's installed in Elasticsearch.", textAnalyzer);
    }

    Map<String, Property> properties = new HashMap<>();
    // IMPORTANT: documentId and sessionId MUST be keyword type for exact matching
    properties.put("documentId", Property.of(p -> p.keyword(k -> k)));
    properties.put("sessionId", Property.of(p -> p.keyword(k -> k)));
    properties.put("fileName", Property.of(p -> p.text(TextProperty.of(t -> t))));
    properties.put("chunkIndex", Property.of(p -> p.integer(i -> i)));
    properties.put(
        "content", Property.of(p -> p.text(TextProperty.of(t -> t.analyzer(textAnalyzer)))));
    properties.put("tokenCount", Property.of(p -> p.integer(i -> i)));
    properties.put(
        "embedding",
        Property.of(
            p ->
                p.denseVector(
                    DenseVectorProperty.of(
                        d -> d.dims(vectorDimensions).index(true).similarity("cosine")))));
    // Metadata fields for enhanced retrieval (RAG optimization Phase 1)
    properties.put(
        "documentTitle", Property.of(p -> p.text(TextProperty.of(t -> t.analyzer(textAnalyzer)))));
    properties.put(
        "sectionTitle", Property.of(p -> p.text(TextProperty.of(t -> t.analyzer(textAnalyzer)))));
    // keywords is an array of tags (["kusto", "gpu", "oversubscription"]), not free-form text
    properties.put("keywords", Property.of(p -> p.keyword(k -> k)));
    properties.put(
        "enrichedContent",
        Property.of(p -> p.text(TextProperty.of(t -> t.analyzer(textAnalyzer)))));

    CreateIndexRequest request =
        CreateIndexRequest.of(c -> c.index(indexName).mappings(m -> m.properties(properties)));

    elasticsearchClient.indices().create(request);
  }

  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "indexChunksFallback")
  public void indexChunks(List<DocumentChunk> chunks) {
    log.info(
        "indexChunks called with {} chunks for session {}",
        chunks.size(),
        chunks.isEmpty() ? "unknown" : chunks.get(0).getSessionId());
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

      for (DocumentChunk chunk : chunks) {
        Map<String, Object> document = new HashMap<>();
        document.put("documentId", chunk.getDocumentId().toString());
        document.put("sessionId", chunk.getSessionId().toString());
        document.put("fileName", chunk.getFileName());
        document.put("chunkIndex", chunk.getChunkIndex());
        document.put("content", chunk.getContent());
        document.put("tokenCount", chunk.getTokenCount());
        document.put("embedding", chunk.getEmbedding());
        // Metadata fields for enhanced retrieval
        if (chunk.getDocumentTitle() != null) {
          document.put("documentTitle", chunk.getDocumentTitle());
        }
        if (chunk.getSectionTitle() != null) {
          document.put("sectionTitle", chunk.getSectionTitle());
        }
        if (chunk.getKeywords() != null && !chunk.getKeywords().isEmpty()) {
          document.put(
              "keywords", chunk.getKeywords()); // Store as array, not comma-separated string
        }
        if (chunk.getEnrichedContent() != null) {
          document.put("enrichedContent", chunk.getEnrichedContent());
        }

        log.info(
            "Indexing chunk {}: sessionId={}, documentId={}, file={}, contentLength={}, embeddingSize={}",
            chunk.getId(),
            chunk.getSessionId(),
            chunk.getDocumentId(),
            chunk.getFileName(),
            chunk.getContent().length(),
            chunk.getEmbedding() != null ? chunk.getEmbedding().size() : 0);
        log.info("===== FULL CHUNK CONTENT START =====");
        log.info("{}", chunk.getContent());
        log.info("===== FULL CHUNK CONTENT END =====");

        bulkBuilder.operations(
            op -> op.index(idx -> idx.index(indexName).id(chunk.getId()).document(document)));
      }

      log.debug("Executing bulk index request to index: {}", indexName);
      BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

      if (response.errors()) {
        log.error("Bulk indexing had errors: {}", response.toString());
        response
            .items()
            .forEach(
                item -> {
                  if (item.error() != null) {
                    log.error("Index error for {}: {}", item.id(), item.error().reason());
                  }
                });
        meterRegistry.counter("elasticsearch.index.errors").increment();
      } else {
        log.info("Successfully indexed {} chunks to Elasticsearch", chunks.size());
        meterRegistry
            .counter("elasticsearch.index.success", "count", String.valueOf(chunks.size()))
            .increment();
      }
    } catch (IOException e) {
      log.error("Failed to index chunks: {}", e.getMessage(), e);
      throw new RuntimeException("Elasticsearch indexing failed", e);
    } finally {
      sample.stop(meterRegistry.timer("elasticsearch.index.duration"));
    }
  }

  @SuppressWarnings("unused")
  private void indexChunksFallback(List<DocumentChunk> chunks, Throwable t) {
    log.warn("Circuit breaker open for Elasticsearch indexing: {}", t.getMessage());
    meterRegistry.counter("elasticsearch.circuitbreaker.open").increment();
  }

  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "vectorSearchFallback")
  public List<DocumentChunk> vectorSearch(UUID sessionId, List<Float> queryEmbedding, int topK) {
    log.info("========== VECTOR SEARCH START ==========");
    log.info(
        "vectorSearch called for session {}, topK={}, embedding size={}",
        sessionId,
        topK,
        queryEmbedding.size());
    log.info("Filtering by sessionId: {}", sessionId);
    log.info(
        "First 5 dimensions of query embedding: {}",
        queryEmbedding.subList(0, Math.min(5, queryEmbedding.size())));
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      // Use knn search at the request level with filter
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .knn(
                          k ->
                              k.field("embedding")
                                  .queryVector(queryEmbedding)
                                  .k(topK)
                                  .numCandidates(topK * 2)
                                  .filter(
                                      f ->
                                          f.term(
                                              t ->
                                                  t.field("sessionId")
                                                      .value(sessionId.toString()))))
                      .size(topK));

      log.info("Executing vector search on index: {}", indexName);
      SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
      List<DocumentChunk> results = mapHitsToChunks(response.hits().hits());
      log.info(
          "Vector search returned {} results (total hits: {})",
          results.size(),
          response.hits().total() != null ? response.hits().total().value() : "unknown");
      if (!results.isEmpty()) {
        for (int i = 0; i < Math.min(3, results.size()); i++) {
          DocumentChunk result = results.get(i);
          log.info(
              "Result {}: sessionId={}, documentId={}, file={}, chunkIndex={}, score={}",
              i,
              result.getSessionId(),
              result.getDocumentId(),
              result.getFileName(),
              result.getChunkIndex(),
              result.getRelevanceScore());
          log.info("===== RETRIEVED CHUNK {} FULL CONTENT START =====", i);
          log.info("{}", result.getContent());
          log.info("===== RETRIEVED CHUNK {} FULL CONTENT END =====", i);
        }
      } else {
        log.warn("Vector search returned NO RESULTS for session {}!", sessionId);
      }
      log.info("========== VECTOR SEARCH END ==========");
      return results;
    } catch (IOException e) {
      log.error("Vector search failed: {}", e.getMessage(), e);
      throw new RuntimeException("Vector search failed", e);
    } finally {
      sample.stop(meterRegistry.timer("elasticsearch.vectorsearch.duration"));
    }
  }

  @SuppressWarnings("unused")
  private List<DocumentChunk> vectorSearchFallback(
      UUID sessionId, List<Float> queryEmbedding, int topK, Throwable t) {
    log.warn("Circuit breaker open for vector search: {}", t.getMessage());
    return List.of();
  }

  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "keywordSearchFallback")
  public List<DocumentChunk> keywordSearch(UUID sessionId, String query, int topK) {
    log.info("========== KEYWORD SEARCH START ==========");
    log.info("keywordSearch called for session {}, query='{}', topK={}", sessionId, query, topK);
    log.info("Filtering by sessionId: {}", sessionId);
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .query(
                          q ->
                              q.bool(
                                  b ->
                                      b.filter(
                                              f ->
                                                  f.term(
                                                      t ->
                                                          t.field("sessionId")
                                                              .value(sessionId.toString())))
                                          .must(
                                              m ->
                                                  m.match(mt -> mt.field("content").query(query)))))
                      .size(topK));

      log.info("Executing keyword search on index: {}", indexName);
      SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
      List<DocumentChunk> results = mapHitsToChunks(response.hits().hits());
      log.info(
          "Keyword search returned {} results (total hits: {})",
          results.size(),
          response.hits().total() != null ? response.hits().total().value() : "unknown");
      if (!results.isEmpty()) {
        for (int i = 0; i < Math.min(3, results.size()); i++) {
          DocumentChunk result = results.get(i);
          log.info(
              "Result {}: sessionId={}, documentId={}, file={}, chunkIndex={}, score={}",
              i,
              result.getSessionId(),
              result.getDocumentId(),
              result.getFileName(),
              result.getChunkIndex(),
              result.getRelevanceScore());
          log.info("===== RETRIEVED CHUNK {} FULL CONTENT START =====", i);
          log.info("{}", result.getContent());
          log.info("===== RETRIEVED CHUNK {} FULL CONTENT END =====", i);
        }
      } else {
        log.warn(
            "Keyword search returned NO RESULTS for session {} and query '{}'!", sessionId, query);
      }
      log.info("========== KEYWORD SEARCH END ==========");
      return results;
    } catch (IOException e) {
      log.error("Keyword search failed: {}", e.getMessage(), e);
      throw new RuntimeException("Keyword search failed", e);
    } finally {
      sample.stop(meterRegistry.timer("elasticsearch.keywordsearch.duration"));
    }
  }

  @SuppressWarnings("unused")
  private List<DocumentChunk> keywordSearchFallback(
      UUID sessionId, String query, int topK, Throwable t) {
    log.warn("Circuit breaker open for keyword search: {}", t.getMessage());
    return List.of();
  }

  public void deleteByDocumentId(UUID documentId) {
    try {
      DeleteByQueryRequest request =
          DeleteByQueryRequest.of(
              d ->
                  d.index(indexName)
                      .query(q -> q.term(t -> t.field("documentId").value(documentId.toString()))));

      elasticsearchClient.deleteByQuery(request);
      log.info("Deleted chunks for document: {}", documentId);
    } catch (IOException e) {
      log.error("Failed to delete chunks for document {}: {}", documentId, e.getMessage());
    }
  }

  public void deleteBySessionId(UUID sessionId) {
    try {
      DeleteByQueryRequest request =
          DeleteByQueryRequest.of(
              d ->
                  d.index(indexName)
                      .query(q -> q.term(t -> t.field("sessionId").value(sessionId.toString()))));

      elasticsearchClient.deleteByQuery(request);
      log.info("Deleted all chunks for session: {}", sessionId);
    } catch (IOException e) {
      log.error("Failed to delete chunks for session {}: {}", sessionId, e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private List<DocumentChunk> mapHitsToChunks(List<Hit<Map>> hits) {
    List<DocumentChunk> chunks = new ArrayList<>();
    for (Hit<Map> hit : hits) {
      Map<String, Object> source = hit.source();
      if (source != null) {
        DocumentChunk.DocumentChunkBuilder builder =
            DocumentChunk.builder()
                .id(hit.id())
                .documentId(UUID.fromString((String) source.get("documentId")))
                .sessionId(UUID.fromString((String) source.get("sessionId")))
                .fileName((String) source.get("fileName"))
                .chunkIndex((Integer) source.get("chunkIndex"))
                .content((String) source.get("content"))
                .tokenCount((Integer) source.get("tokenCount"));

        // Capture relevance score from search results
        if (hit.score() != null) {
          builder.relevanceScore(hit.score());
        }

        // Include metadata fields if present
        if (source.get("documentTitle") != null) {
          builder.documentTitle((String) source.get("documentTitle"));
        }
        if (source.get("sectionTitle") != null) {
          builder.sectionTitle((String) source.get("sectionTitle"));
        }
        if (source.get("keywords") != null) {
          @SuppressWarnings("unchecked")
          List<String> keywords = (List<String>) source.get("keywords");
          builder.keywords(keywords);
        }
        if (source.get("enrichedContent") != null) {
          builder.enrichedContent((String) source.get("enrichedContent"));
        }

        chunks.add(builder.build());
      }
    }
    return chunks;
  }
}
