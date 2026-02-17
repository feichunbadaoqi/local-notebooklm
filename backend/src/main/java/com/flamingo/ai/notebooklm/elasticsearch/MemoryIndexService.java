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
 * Elasticsearch index service for memories.
 *
 * <p>Manages indexing and search of memories with vector embeddings for semantic search over
 * extracted memories.
 */
@Service
@Slf4j
public class MemoryIndexService extends AbstractElasticsearchIndexService<MemoryDocument> {

  @Value("${app.elasticsearch.memory-index-name:notebooklm-memories}")
  private String indexName;

  @Value("${app.elasticsearch.vector-dimensions:3072}")
  private int vectorDimensions;

  public MemoryIndexService(ElasticsearchClient elasticsearchClient, MeterRegistry meterRegistry) {
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
                        d ->
                            d.dims(vectorDimensions)
                                .index(true)
                                .similarity(DenseVectorSimilarity.Cosine)))));
    return properties;
  }

  @Override 
  protected Map<String, Object> convertToDocument(MemoryDocument memory) {
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

  @Override
  @SuppressWarnings("unchecked")
  protected MemoryDocument convertFromDocument(Map<String, Object> source) {
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

  @Override
  protected String getDocumentId(MemoryDocument entity) {
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
                                    .must(
                                        m ->
                                            m.match(
                                                mt -> mt.field("memoryContent").query(query))))));
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
    return "memory";
  }

  // Convenience methods for backward compatibility

  /**
   * Indexes memories to Elasticsearch (convenience method).
   *
   * @param memories list of memories to index
   */
  public void indexMemories(List<MemoryDocument> memories) {
    indexDocuments(memories);
  }

  /**
   * Performs vector search on memories (convenience method).
   *
   * @param sessionId the session ID to filter by
   * @param queryEmbedding the query embedding vector 
   * @param topK number of results to return
   * @return list of matching memories
   */
  public List<MemoryDocument> vectorSearch(UUID sessionId, List<Float> queryEmbedding, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return vectorSearch(criteria, queryEmbedding, topK);
  }

  /**
   * Performs keyword search on memories (convenience method).
   *
   * @param sessionId the session ID to filter by
   * @param query the search query
   * @param topK number of results to return
   * @return list of matching memories
   */
  public List<MemoryDocument> keywordSearch(UUID sessionId, String query, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return keywordSearch(criteria, query, topK);
  }

  /**
   * Deletes all memories for a session (convenience method).
   *
   * @param sessionId the session ID
   */
  public void deleteBySessionId(UUID sessionId) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    deleteBy(criteria);
  }
}
