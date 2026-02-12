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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Service for managing document chunks in Elasticsearch with vector and keyword search. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexService {

  private final ElasticsearchClient elasticsearchClient;
  private final MeterRegistry meterRegistry;

  @Value("${app.elasticsearch.index-name:notebooklm-chunks}")
  private String indexName;

  @Value("${app.elasticsearch.vector-dimensions:1536}")
  private int vectorDimensions;

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
    Map<String, Property> properties = new HashMap<>();
    properties.put("documentId", Property.of(p -> p.keyword(k -> k)));
    properties.put("sessionId", Property.of(p -> p.keyword(k -> k)));
    properties.put("fileName", Property.of(p -> p.text(TextProperty.of(t -> t))));
    properties.put("chunkIndex", Property.of(p -> p.integer(i -> i)));
    properties.put(
        "content", Property.of(p -> p.text(TextProperty.of(t -> t.analyzer("standard")))));
    properties.put("tokenCount", Property.of(p -> p.integer(i -> i)));
    properties.put(
        "embedding",
        Property.of(
            p ->
                p.denseVector(
                    DenseVectorProperty.of(
                        d -> d.dims(vectorDimensions).index(true).similarity("cosine")))));

    CreateIndexRequest request =
        CreateIndexRequest.of(c -> c.index(indexName).mappings(m -> m.properties(properties)));

    elasticsearchClient.indices().create(request);
  }

  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "indexChunksFallback")
  public void indexChunks(List<DocumentChunk> chunks) {
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

        bulkBuilder.operations(
            op -> op.index(idx -> idx.index(indexName).id(chunk.getId()).document(document)));
      }

      BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

      if (response.errors()) {
        log.error("Bulk indexing had errors");
        meterRegistry.counter("elasticsearch.index.errors").increment();
      } else {
        meterRegistry
            .counter("elasticsearch.index.success", "count", String.valueOf(chunks.size()))
            .increment();
      }
    } catch (IOException e) {
      log.error("Failed to index chunks: {}", e.getMessage());
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

      SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
      return mapHitsToChunks(response.hits().hits());
    } catch (IOException e) {
      log.error("Vector search failed: {}", e.getMessage());
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

      SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
      return mapHitsToChunks(response.hits().hits());
    } catch (IOException e) {
      log.error("Keyword search failed: {}", e.getMessage());
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
        chunks.add(
            DocumentChunk.builder()
                .id(hit.id())
                .documentId(UUID.fromString((String) source.get("documentId")))
                .sessionId(UUID.fromString((String) source.get("sessionId")))
                .fileName((String) source.get("fileName"))
                .chunkIndex((Integer) source.get("chunkIndex"))
                .content((String) source.get("content"))
                .tokenCount((Integer) source.get("tokenCount"))
                .build());
      }
    }
    return chunks;
  }
}
