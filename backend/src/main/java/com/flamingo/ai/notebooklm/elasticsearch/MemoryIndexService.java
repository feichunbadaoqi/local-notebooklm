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
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
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

/**
 * Elasticsearch index service for memories.
 *
 * <p>Manages indexing and search of memories with vector embeddings for semantic search over
 * extracted memories.
 */
@Service
@Slf4j
public class MemoryIndexService {

  private final ElasticsearchClient elasticsearchClient;
  private final MeterRegistry meterRegistry;

  @Value("${app.elasticsearch.memory-index-name:notebooklm-memories}")
  private String indexName;

  @Value("${app.elasticsearch.vector-dimensions:3072}")
  private int vectorDimensions;

  public MemoryIndexService(ElasticsearchClient elasticsearchClient, MeterRegistry meterRegistry) {
    this.elasticsearchClient = elasticsearchClient;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  public void initIndex() {
    try {
      var indices = elasticsearchClient.indices();
      if (indices == null) {
        log.warn("Elasticsearch client not available, skipping memory index initialization");
        return;
      }
      boolean exists = indices.exists(e -> e.index(indexName)).value();
      if (!exists) {
        createIndex();
        log.info("Created Elasticsearch memory index: {}", indexName);
      }
    } catch (Exception e) {
      log.warn("Could not check/create Elasticsearch memory index: {}", e.getMessage());
    }
  }

  private void createIndex() throws IOException {
    Map<String, Property> properties = new HashMap<>();
    properties.put("sessionId", Property.of(p -> p.keyword(k -> k)));
    properties.put("memoryContent", Property.of(p -> p.text(TextProperty.of(t -> t))));
    properties.put("memoryType", Property.of(p -> p.keyword(k -> k)));
    properties.put("importance", Property.of(p -> p.float_(f -> f)));
    properties.put("timestamp", Property.of(p -> p.long_(l -> l)));
    properties.put(
        "embedding",
        Property.of(
            p ->
                p.denseVector(
                    DenseVectorProperty.of(
                        d -> d.dims(vectorDimensions).index(true).similarity("cosine")))));

    CreateIndexRequest createIndexRequest =
        CreateIndexRequest.of(c -> c.index(indexName).mappings(m -> m.properties(properties)));

    elasticsearchClient.indices().create(createIndexRequest);
  }

  /**
   * Indexes memories to Elasticsearch.
   *
   * @param memories list of memories to index
   */
  @Timed(value = "memory.index", description = "Time to index memories")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "indexMemoriesFallback")
  public void indexMemories(List<MemoryDocument> memories) {
    if (memories.isEmpty()) {
      return;
    }

    try {
      BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
      for (MemoryDocument memory : memories) {
        bulkBuilder.operations(
            op ->
                op.index(
                    idx ->
                        idx.index(indexName)
                            .id(memory.getId())
                            .document(convertToElasticsearchDoc(memory))));
      }

      BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
      if (response.errors()) {
        log.warn("Some memories failed to index: {}", response.items());
      }

      meterRegistry.counter("memory.indexed").increment(memories.size());
      log.debug("Indexed {} memories", memories.size());
    } catch (Exception e) {
      log.error("Failed to index memories: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to index memories", e);
    }
  }

  @SuppressWarnings("unused")
  private void indexMemoriesFallback(List<MemoryDocument> memories, Throwable t) {
    log.warn("Memory indexing fallback triggered: {}", t.getMessage());
    meterRegistry.counter("memory.index.fallback").increment();
  }

  /**
   * Performs vector search on memories.
   *
   * @param sessionId the session ID to filter by
   * @param queryEmbedding the query embedding vector
   * @param topK number of results to return
   * @return list of matching memories
   */
  @Timed(value = "memory.vector_search", description = "Time to vector search memories")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "vectorSearchFallback")
  public List<MemoryDocument> vectorSearch(UUID sessionId, List<Float> queryEmbedding, int topK) {
    try {
      SearchRequest searchRequest =
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

      SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
      List<MemoryDocument> results = new ArrayList<>();
      for (Hit<Map> hit : response.hits().hits()) {
        MemoryDocument doc = convertFromElasticsearchDoc(hit.source());
        doc.setRelevanceScore(hit.score() != null ? hit.score() : 0.0);
        results.add(doc);
      }

      meterRegistry.counter("memory.vector_search").increment();
      return results;
    } catch (Exception e) {
      log.error("Vector search failed for memories: {}", e.getMessage(), e);
      throw new RuntimeException("Vector search failed", e);
    }
  }

  @SuppressWarnings("unused")
  private List<MemoryDocument> vectorSearchFallback(
      UUID sessionId, List<Float> queryEmbedding, int topK, Throwable t) {
    log.warn("Memory vector search fallback triggered: {}", t.getMessage());
    meterRegistry.counter("memory.vector_search.fallback").increment();
    return List.of();
  }

  /**
   * Performs keyword search on memories.
   *
   * @param sessionId the session ID to filter by
   * @param query the search query
   * @param topK number of results to return
   * @return list of matching memories
   */
  @Timed(value = "memory.keyword_search", description = "Time to keyword search memories")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "keywordSearchFallback")
  public List<MemoryDocument> keywordSearch(UUID sessionId, String query, int topK) {
    try {
      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(topK)
                      .query(
                          q ->
                              q.bool(
                                  b ->
                                      b.must(
                                              m ->
                                                  m.term(
                                                      t ->
                                                          t.field("sessionId")
                                                              .value(sessionId.toString())))
                                          .must(
                                              m ->
                                                  m.match(
                                                      mt ->
                                                          mt.field("memoryContent")
                                                              .query(query))))));

      SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
      List<MemoryDocument> results = new ArrayList<>();
      for (Hit<Map> hit : response.hits().hits()) {
        MemoryDocument doc = convertFromElasticsearchDoc(hit.source());
        doc.setRelevanceScore(hit.score() != null ? hit.score() : 0.0);
        results.add(doc);
      }

      meterRegistry.counter("memory.keyword_search").increment();
      return results;
    } catch (Exception e) {
      log.error("Keyword search failed for memories: {}", e.getMessage(), e);
      throw new RuntimeException("Keyword search failed", e);
    }
  }

  @SuppressWarnings("unused")
  private List<MemoryDocument> keywordSearchFallback(
      UUID sessionId, String query, int topK, Throwable t) {
    log.warn("Memory keyword search fallback triggered: {}", t.getMessage());
    meterRegistry.counter("memory.keyword_search.fallback").increment();
    return List.of();
  }

  /**
   * Deletes all memories for a session.
   *
   * @param sessionId the session ID
   */
  @Timed(value = "memory.delete_by_session", description = "Time to delete memories by session")
  public void deleteBySessionId(UUID sessionId) {
    try {
      DeleteByQueryRequest deleteRequest =
          DeleteByQueryRequest.of(
              d ->
                  d.index(indexName)
                      .query(q -> q.term(t -> t.field("sessionId").value(sessionId.toString()))));

      elasticsearchClient.deleteByQuery(deleteRequest);
      log.info("Deleted memories for session: {}", sessionId);
      meterRegistry.counter("memory.deleted_by_session").increment();
    } catch (Exception e) {
      log.error("Failed to delete memories for session {}: {}", sessionId, e.getMessage(), e);
      throw new RuntimeException("Failed to delete memories", e);
    }
  }

  /** Refreshes the index. */
  public void refresh() {
    try {
      elasticsearchClient.indices().refresh(r -> r.index(indexName));
    } catch (Exception e) {
      log.warn("Failed to refresh memory index: {}", e.getMessage());
    }
  }

  public String getIndexName() {
    return indexName;
  }

  private Map<String, Object> convertToElasticsearchDoc(MemoryDocument memory) {
    Map<String, Object> doc = new HashMap<>();
    doc.put("sessionId", memory.getSessionId().toString());
    doc.put("memoryContent", memory.getMemoryContent());
    doc.put("memoryType", memory.getMemoryType());
    if (memory.getImportance() != null) {
      doc.put("importance", memory.getImportance());
    }
    if (memory.getEmbedding() != null) {
      doc.put("embedding", memory.getEmbedding());
    }
    if (memory.getTimestamp() != null) {
      doc.put("timestamp", memory.getTimestamp());
    }
    return doc;
  }

  @SuppressWarnings("unchecked")
  private MemoryDocument convertFromElasticsearchDoc(Map<String, Object> source) {
    return MemoryDocument.builder()
        .id((String) source.get("id"))
        .sessionId(UUID.fromString((String) source.get("sessionId")))
        .memoryContent((String) source.get("memoryContent"))
        .memoryType((String) source.get("memoryType"))
        .importance(
            source.get("importance") != null
                ? ((Number) source.get("importance")).floatValue()
                : null)
        .embedding(source.get("embedding") != null ? (List<Float>) source.get("embedding") : null)
        .timestamp(
            source.get("timestamp") != null ? ((Number) source.get("timestamp")).longValue() : null)
        .build();
  }
}
