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
 * Elasticsearch index service for chat messages.
 *
 * <p>Manages indexing and search of chat messages with vector embeddings for semantic search over
 * conversation history.
 */
@Service
@Slf4j
public class ChatMessageIndexService {

  private final ElasticsearchClient elasticsearchClient;
  private final MeterRegistry meterRegistry;

  @Value("${app.elasticsearch.chat-message-index-name:notebooklm-chat-messages}")
  private String indexName;

  @Value("${app.elasticsearch.vector-dimensions:3072}")
  private int vectorDimensions;

  public ChatMessageIndexService(
      ElasticsearchClient elasticsearchClient, MeterRegistry meterRegistry) {
    this.elasticsearchClient = elasticsearchClient;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  public void initIndex() {
    try {
      var indices = elasticsearchClient.indices();
      if (indices == null) {
        log.warn("Elasticsearch client not available, skipping chat message index initialization");
        return;
      }
      boolean exists = indices.exists(e -> e.index(indexName)).value();
      if (!exists) {
        createIndex();
        log.info("Created Elasticsearch chat message index: {}", indexName);
      }
    } catch (Exception e) {
      log.warn("Could not check/create Elasticsearch chat message index: {}", e.getMessage());
    }
  }

  private void createIndex() throws IOException {
    Map<String, Property> properties = new HashMap<>();
    properties.put("sessionId", Property.of(p -> p.keyword(k -> k)));
    properties.put("role", Property.of(p -> p.keyword(k -> k)));
    properties.put("content", Property.of(p -> p.text(TextProperty.of(t -> t))));
    properties.put("timestamp", Property.of(p -> p.long_(l -> l)));
    properties.put("tokenCount", Property.of(p -> p.integer(i -> i)));
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
   * Indexes chat messages to Elasticsearch.
   *
   * @param messages list of chat messages to index
   */
  @Timed(value = "chat_message.index", description = "Time to index chat messages")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "indexMessagesFallback")
  public void indexMessages(List<ChatMessageDocument> messages) {
    if (messages.isEmpty()) {
      return;
    }

    try {
      BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
      for (ChatMessageDocument message : messages) {
        bulkBuilder.operations(
            op ->
                op.index(
                    idx ->
                        idx.index(indexName)
                            .id(message.getId())
                            .document(convertToElasticsearchDoc(message))));
      }

      BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
      if (response.errors()) {
        log.warn("Some chat messages failed to index: {}", response.items());
      }

      meterRegistry.counter("chat_message.indexed").increment(messages.size());
      log.debug("Indexed {} chat messages", messages.size());
    } catch (Exception e) {
      log.error("Failed to index chat messages: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to index chat messages", e);
    }
  }

  @SuppressWarnings("unused")
  private void indexMessagesFallback(List<ChatMessageDocument> messages, Throwable t) {
    log.warn("Chat message indexing fallback triggered: {}", t.getMessage());
    meterRegistry.counter("chat_message.index.fallback").increment();
  }

  /**
   * Performs vector search on chat messages.
   *
   * @param sessionId the session ID to filter by
   * @param queryEmbedding the query embedding vector
   * @param topK number of results to return
   * @return list of matching chat messages
   */
  @Timed(value = "chat_message.vector_search", description = "Time to vector search chat messages")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "vectorSearchFallback")
  public List<ChatMessageDocument> vectorSearch(
      UUID sessionId, List<Float> queryEmbedding, int topK) {
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
      List<ChatMessageDocument> results = new ArrayList<>();
      for (Hit<Map> hit : response.hits().hits()) {
        ChatMessageDocument doc = convertFromElasticsearchDoc(hit.source());
        doc.setRelevanceScore(hit.score() != null ? hit.score() : 0.0);
        results.add(doc);
      }

      meterRegistry.counter("chat_message.vector_search").increment();
      return results;
    } catch (Exception e) {
      log.error("Vector search failed for chat messages: {}", e.getMessage(), e);
      throw new RuntimeException("Vector search failed", e);
    }
  }

  @SuppressWarnings("unused")
  private List<ChatMessageDocument> vectorSearchFallback(
      UUID sessionId, List<Float> queryEmbedding, int topK, Throwable t) {
    log.warn("Chat message vector search fallback triggered: {}", t.getMessage());
    meterRegistry.counter("chat_message.vector_search.fallback").increment();
    return List.of();
  }

  /**
   * Performs keyword search on chat messages.
   *
   * @param sessionId the session ID to filter by
   * @param query the search query
   * @param topK number of results to return
   * @return list of matching chat messages
   */
  @Timed(
      value = "chat_message.keyword_search",
      description = "Time to keyword search chat messages")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "keywordSearchFallback")
  public List<ChatMessageDocument> keywordSearch(UUID sessionId, String query, int topK) {
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
                                                      mt -> mt.field("content").query(query))))));

      SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
      List<ChatMessageDocument> results = new ArrayList<>();
      for (Hit<Map> hit : response.hits().hits()) {
        ChatMessageDocument doc = convertFromElasticsearchDoc(hit.source());
        doc.setRelevanceScore(hit.score() != null ? hit.score() : 0.0);
        results.add(doc);
      }

      meterRegistry.counter("chat_message.keyword_search").increment();
      return results;
    } catch (Exception e) {
      log.error("Keyword search failed for chat messages: {}", e.getMessage(), e);
      throw new RuntimeException("Keyword search failed", e);
    }
  }

  @SuppressWarnings("unused")
  private List<ChatMessageDocument> keywordSearchFallback(
      UUID sessionId, String query, int topK, Throwable t) {
    log.warn("Chat message keyword search fallback triggered: {}", t.getMessage());
    meterRegistry.counter("chat_message.keyword_search.fallback").increment();
    return List.of();
  }

  /**
   * Deletes all chat messages for a session.
   *
   * @param sessionId the session ID
   */
  @Timed(
      value = "chat_message.delete_by_session",
      description = "Time to delete chat messages by session")
  public void deleteBySessionId(UUID sessionId) {
    try {
      DeleteByQueryRequest deleteRequest =
          DeleteByQueryRequest.of(
              d ->
                  d.index(indexName)
                      .query(q -> q.term(t -> t.field("sessionId").value(sessionId.toString()))));

      elasticsearchClient.deleteByQuery(deleteRequest);
      log.info("Deleted chat messages for session: {}", sessionId);
      meterRegistry.counter("chat_message.deleted_by_session").increment();
    } catch (Exception e) {
      log.error("Failed to delete chat messages for session {}: {}", sessionId, e.getMessage(), e);
      throw new RuntimeException("Failed to delete chat messages", e);
    }
  }

  /** Refreshes the index. */
  public void refresh() {
    try {
      elasticsearchClient.indices().refresh(r -> r.index(indexName));
    } catch (Exception e) {
      log.warn("Failed to refresh chat message index: {}", e.getMessage());
    }
  }

  public String getIndexName() {
    return indexName;
  }

  private Map<String, Object> convertToElasticsearchDoc(ChatMessageDocument message) {
    Map<String, Object> doc = new HashMap<>();
    doc.put("sessionId", message.getSessionId().toString());
    doc.put("role", message.getRole());
    doc.put("content", message.getContent());
    if (message.getEmbedding() != null) {
      doc.put("embedding", message.getEmbedding());
    }
    if (message.getTimestamp() != null) {
      doc.put("timestamp", message.getTimestamp());
    }
    if (message.getTokenCount() != null) {
      doc.put("tokenCount", message.getTokenCount());
    }
    return doc;
  }

  @SuppressWarnings("unchecked")
  private ChatMessageDocument convertFromElasticsearchDoc(Map<String, Object> source) {
    return ChatMessageDocument.builder()
        .id((String) source.get("id"))
        .sessionId(UUID.fromString((String) source.get("sessionId")))
        .role((String) source.get("role"))
        .content((String) source.get("content"))
        .embedding(source.get("embedding") != null ? (List<Float>) source.get("embedding") : null)
        .timestamp(
            source.get("timestamp") != null ? ((Number) source.get("timestamp")).longValue() : null)
        .tokenCount(
            source.get("tokenCount") != null
                ? ((Number) source.get("tokenCount")).intValue()
                : null)
        .build();
  }
}
