package com.flamingo.ai.notebooklm.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.micrometer.core.instrument.MeterRegistry;
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
public class ChatMessageIndexService
    extends AbstractElasticsearchIndexService<ChatMessageDocument> {

  @Value("${app.elasticsearch.chat-message-index-name:notebooklm-chat-messages}")
  private String indexName;

  @Value("${app.elasticsearch.vector-dimensions:3072}")
  private int vectorDimensions;

  public ChatMessageIndexService(
      ElasticsearchClient elasticsearchClient, MeterRegistry meterRegistry) {
    super(elasticsearchClient, meterRegistry);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  protected int getVectorDimensions() {
    return vectorDimensions;
  }

  @Override
  protected Map<String, Property> defineIndexProperties() {
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
                        d ->
                            d.dims(vectorDimensions)
                                .index(true)
                                .similarity(DenseVectorSimilarity.Cosine)))));
    return properties;
  }

  @Override
  protected Map<String, Object> convertToDocument(ChatMessageDocument message) {
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

  @Override
  @SuppressWarnings("unchecked")
  protected ChatMessageDocument convertFromDocument(Map<String, Object> source) {
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

  @Override
  protected String getDocumentId(ChatMessageDocument entity) {
    return entity.getId();
  }

  @Override
  protected SearchRequest buildVectorSearchRequest(
      Map<String, Object> filterCriteria,
      List<Float> queryEmbedding,
      int topK,
      String embeddingField) {
    UUID sessionId = (UUID) filterCriteria.get("sessionId");
    if (sessionId == null) {
      throw new IllegalArgumentException("sessionId filter is required for vector search");
    }

    return SearchRequest.of(
        s ->
            s.index(indexName)
                .knn(
                    k ->
                        k.field(embeddingField)
                            .queryVector(queryEmbedding)
                            .k(topK)
                            .numCandidates(topK * 2)
                            .filter(
                                f -> f.term(t -> t.field("sessionId").value(sessionId.toString()))))
                .size(topK));
  }

  @Override
  protected SearchRequest buildKeywordSearchRequest(
      Map<String, Object> filterCriteria, String query, int topK) {
    UUID sessionId = (UUID) filterCriteria.get("sessionId");
    if (sessionId == null) {
      throw new IllegalArgumentException("sessionId filter is required for keyword search");
    }

    return SearchRequest.of(
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
                                    .must(m -> m.match(mt -> mt.field("content").query(query))))));
  }

  @Override
  protected Query buildDeleteQuery(Map<String, Object> criteria) {
    if (criteria.containsKey("sessionId")) {
      UUID sessionId = (UUID) criteria.get("sessionId");
      return Query.of(q -> q.term(t -> t.field("sessionId").value(sessionId.toString())));
    } else {
      throw new IllegalArgumentException("deleteBy requires sessionId in criteria");
    }
  }

  @Override
  protected String getMetricPrefix() {
    return "chat_message";
  }

  // Convenience methods for backward compatibility

  /**
   * Indexes chat messages to Elasticsearch (convenience method).
   *
   * @param messages list of chat messages to index
   */
  public void indexMessages(List<ChatMessageDocument> messages) {
    indexDocuments(messages);
  }

  /**
   * Performs vector search on chat messages (convenience method).
   *
   * @param sessionId the session ID to filter by
   * @param queryEmbedding the query embedding vector
   * @param topK number of results to return
   * @return list of matching chat messages
   */
  public List<ChatMessageDocument> vectorSearch(
      UUID sessionId, List<Float> queryEmbedding, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return vectorSearch(criteria, queryEmbedding, topK);
  }

  /**
   * Performs keyword search on chat messages (convenience method).
   *
   * @param sessionId the session ID to filter by
   * @param query the search query
   * @param topK number of results to return
   * @return list of matching chat messages
   */
  public List<ChatMessageDocument> keywordSearch(UUID sessionId, String query, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return keywordSearch(criteria, query, topK);
  }

  /**
   * Deletes all chat messages for a session (convenience method).
   *
   * @param sessionId the session ID
   */
  public void deleteBySessionId(UUID sessionId) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    deleteBy(criteria);
  }
}
