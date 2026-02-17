package com.flamingo.ai.notebooklm.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
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
 * Elasticsearch index service for DocumentChunk documents.
 *
 * <p>Manages indexing and search of document chunks with vector embeddings, supporting hybrid
 * search (vector + BM25) for RAG (Retrieval Augmented Generation).
 */
@Service
@Slf4j
public class DocumentChunkIndexService extends AbstractElasticsearchIndexService<DocumentChunk> {

  @Value("${app.elasticsearch.index-name:notebooklm-chunks}")
  private String indexName;

  @Value("${app.elasticsearch.vector-dimensions:3072}")
  private int vectorDimensions;

  @Value("${app.elasticsearch.text-analyzer:standard}")
  private String textAnalyzer;

  @org.springframework.beans.factory.annotation.Autowired
  public DocumentChunkIndexService(
      ElasticsearchClient elasticsearchClient, MeterRegistry meterRegistry) {
    super(elasticsearchClient, meterRegistry);
  }

  /** Constructor for testing - allows setting index name and vector dimensions. */
  public DocumentChunkIndexService(
      ElasticsearchClient elasticsearchClient,
      MeterRegistry meterRegistry,
      String indexName,
      int vectorDimensions) {
    super(elasticsearchClient, meterRegistry);
    this.indexName = indexName;
    this.vectorDimensions = vectorDimensions;
    this.textAnalyzer = "standard"; // Default for tests
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
                        d ->
                            d.dims(vectorDimensions)
                                .index(true)
                                .similarity(DenseVectorSimilarity.Cosine)))));
    // Multiple embeddings for improved retrieval (Stage 2.2)
    properties.put(
        "titleEmbedding",
        Property.of(
            p ->
                p.denseVector(
                    DenseVectorProperty.of(
                        d ->
                            d.dims(vectorDimensions)
                                .index(true)
                                .similarity(DenseVectorSimilarity.Cosine)))));
    properties.put(
        "contentEmbedding",
        Property.of(
            p ->
                p.denseVector(
                    DenseVectorProperty.of(
                        d ->
                            d.dims(vectorDimensions)
                                .index(true)
                                .similarity(DenseVectorSimilarity.Cosine)))));
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

    return properties;
  }

  @Override
  protected Map<String, Object> convertToDocument(DocumentChunk chunk) {
    Map<String, Object> document = new HashMap<>();
    document.put("documentId", chunk.getDocumentId().toString());
    document.put("sessionId", chunk.getSessionId().toString());
    document.put("fileName", chunk.getFileName());
    document.put("chunkIndex", chunk.getChunkIndex());
    document.put("content", chunk.getContent());
    document.put("tokenCount", chunk.getTokenCount());
    document.put("embedding", chunk.getEmbedding());
    // Multiple embeddings (Stage 2.2)
    if (chunk.getTitleEmbedding() != null) {
      document.put("titleEmbedding", chunk.getTitleEmbedding());
    }
    if (chunk.getContentEmbedding() != null) {
      document.put("contentEmbedding", chunk.getContentEmbedding());
    }
    // Metadata fields for enhanced retrieval
    if (chunk.getDocumentTitle() != null) {
      document.put("documentTitle", chunk.getDocumentTitle());
    }
    if (chunk.getSectionTitle() != null) {
      document.put("sectionTitle", chunk.getSectionTitle());
    }
    if (chunk.getKeywords() != null && !chunk.getKeywords().isEmpty()) {
      document.put("keywords", chunk.getKeywords()); // Store as array, not comma-separated string
    }
    if (chunk.getEnrichedContent() != null) {
      document.put("enrichedContent", chunk.getEnrichedContent());
    }
    return document;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected DocumentChunk convertFromDocument(Map<String, Object> source) {
    DocumentChunk.DocumentChunkBuilder builder =
        DocumentChunk.builder()
            .id((String) source.get("id"))
            .documentId(UUID.fromString((String) source.get("documentId")))
            .sessionId(UUID.fromString((String) source.get("sessionId")))
            .fileName((String) source.get("fileName"))
            .chunkIndex((Integer) source.get("chunkIndex"))
            .content((String) source.get("content"))
            .tokenCount((Integer) source.get("tokenCount"));

    // Include metadata fields if present
    if (source.get("documentTitle") != null) {
      builder.documentTitle((String) source.get("documentTitle"));
    }
    if (source.get("sectionTitle") != null) {
      builder.sectionTitle((String) source.get("sectionTitle"));
    }
    if (source.get("keywords") != null) {
      List<String> keywords = (List<String>) source.get("keywords");
      builder.keywords(keywords);
    }
    if (source.get("enrichedContent") != null) {
      builder.enrichedContent((String) source.get("enrichedContent"));
    }

    return builder.build();
  }

  @Override
  protected String getDocumentId(DocumentChunk entity) {
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

    log.info("========== VECTOR SEARCH START ==========");
    log.info(
        "vectorSearch called for session {}, topK={}, embedding size={}, field={}",
        sessionId,
        topK,
        queryEmbedding.size(),
        embeddingField);
    log.info("Filtering by sessionId: {}", sessionId);
    log.info(
        "First 5 dimensions of query embedding: {}",
        queryEmbedding.subList(0, Math.min(5, queryEmbedding.size())));

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

    log.info("========== KEYWORD SEARCH START ==========");
    log.info("keywordSearch called for session {}, query='{}', topK={}", sessionId, query, topK);
    log.info("Filtering by sessionId: {}", sessionId);

    return SearchRequest.of(
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
                                            m.multiMatch(
                                                mm ->
                                                    mm.fields(
                                                            "documentTitle^3.0", // 3x boost for
                                                            // title
                                                            "sectionTitle^2.0", // 2x boost for
                                                            // section
                                                            "fileName^1.5", // 1.5x boost for
                                                            // filename
                                                            "content^1.0") // Baseline for content
                                                        .query(query)
                                                        .type(TextQueryType.BestFields) // Use best
                                                        // field score
                                                        .tieBreaker(0.3))))) // Consider other
                // fields (30%)
                .size(topK));
  }

  @Override
  protected Query buildDeleteQuery(Map<String, Object> criteria) {
    if (criteria.containsKey("documentId")) {
      UUID documentId = (UUID) criteria.get("documentId");
      return Query.of(q -> q.term(t -> t.field("documentId").value(documentId.toString())));
    } else if (criteria.containsKey("sessionId")) {
      UUID sessionId = (UUID) criteria.get("sessionId");
      return Query.of(q -> q.term(t -> t.field("sessionId").value(sessionId.toString())));
    } else {
      throw new IllegalArgumentException(
          "deleteBy requires either documentId or sessionId in criteria");
    }
  }

  @Override
  protected String getMetricPrefix() {
    return "document_chunk";
  }

  // Convenience methods for backward compatibility

  /** Indexes document chunks (convenience method). */
  public void indexChunks(List<DocumentChunk> chunks) {
    indexDocuments(chunks);
  }

  /**
   * Performs hybrid search using Elasticsearch's native RRF retriever. Combines BM25 keyword search
   * with dual kNN vector searches (title + content embeddings) in a single server-side query.
   *
   * <p>This method eliminates the need for application-side RRF fusion by leveraging
   * Elasticsearch's built-in retriever API (available in ES 8.14+, GA in 8.16).
   *
   * @param sessionId Session filter
   * @param query Text query for BM25
   * @param queryEmbedding Vector for kNN searches
   * @param topK Number of results to return
   * @return Top K documents ranked by RRF fusion
   */
  public List<DocumentChunk> hybridSearchWithRRF(
      UUID sessionId, String query, List<Float> queryEmbedding, int topK) {
    log.debug("hybridSearchWithRRF for session {}, topK={}", sessionId, topK);

    try {
      String requestJson =
          String.format(
              """
              {
                "retriever": {
                  "rrf": {
                    "retrievers": [
                      {
                        "standard": {
                          "query": {
                            "bool": {
                              "filter": [
                                { "term": { "sessionId": "%s" } }
                              ],
                              "must": [
                                {
                                  "multi_match": {
                                    "query": "%s",
                                    "fields": ["documentTitle^3.0", "sectionTitle^2.0", "fileName^1.5", "content^1.0"],
                                    "type": "best_fields",
                                    "tie_breaker": 0.3
                                  }
                                }
                              ]
                            }
                          }
                        }
                      },
                      {
                        "knn": {
                          "field": "titleEmbedding",
                          "query_vector": %s,
                          "k": %d,
                          "num_candidates": %d,
                          "filter": [
                            { "term": { "sessionId": "%s" } }
                          ]
                        }
                      },
                      {
                        "knn": {
                          "field": "contentEmbedding",
                          "query_vector": %s,
                          "k": %d,
                          "num_candidates": %d,
                          "filter": [
                            { "term": { "sessionId": "%s" } }
                          ]
                        }
                      }
                    ],
                    "rank_constant": 60,
                    "rank_window_size": 50
                  }
                },
                "size": %d
              }
              """,
              sessionId.toString(),
              escapeJson(query),
              embeddingVectorToJson(queryEmbedding),
              topK,
              topK * 2,
              sessionId.toString(),
              embeddingVectorToJson(queryEmbedding),
              topK,
              topK * 2,
              sessionId.toString(),
              topK);

      var response =
          elasticsearchClient.search(
              s -> s.index(indexName).withJson(new java.io.StringReader(requestJson)), Map.class);

      List<DocumentChunk> results = new java.util.ArrayList<>();
      for (var hit : response.hits().hits()) {
        Map<String, Object> source = hit.source();
        if (source != null) {
          source.put("id", hit.id());
          DocumentChunk chunk = convertFromDocument(source);
          chunk.setRelevanceScore(hit.score() != null ? hit.score() : 0.0);
          results.add(chunk);
        }
      }

      log.debug("Hybrid search with RRF returned {} results", results.size());
      return results;

    } catch (Exception e) {
      log.error("Hybrid search with RRF failed for session {}", sessionId, e);
      // Fallback to legacy vector search
      log.warn("Falling back to legacy vector search");
      return vectorSearch(sessionId, queryEmbedding, topK);
    }
  }

  /** Escapes special characters in JSON strings. */
  private String escapeJson(String str) {
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  /** Converts embedding vector to JSON array string. */
  private String embeddingVectorToJson(List<Float> vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.size(); i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(vector.get(i));
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * @deprecated Use {@link #hybridSearchWithRRF} for native RRF hybrid search.
   */
  @Deprecated
  public List<DocumentChunk> vectorSearch(UUID sessionId, List<Float> queryEmbedding, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return vectorSearch(criteria, queryEmbedding, topK);
  }

  /**
   * @deprecated Use {@link #hybridSearchWithRRF} for native RRF hybrid search.
   */
  @Deprecated
  public List<DocumentChunk> keywordSearch(UUID sessionId, String query, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return keywordSearch(criteria, query, topK);
  }

  /**
   * @deprecated Use {@link #hybridSearchWithRRF} for native RRF hybrid search.
   */
  @Deprecated
  public List<DocumentChunk> vectorSearchByField(
      UUID sessionId, String embeddingField, List<Float> queryEmbedding, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return vectorSearchByField(criteria, embeddingField, queryEmbedding, topK);
  }

  /**
   * Deletes all chunks for a document (convenience method).
   *
   * @param documentId the document ID
   */
  public void deleteByDocumentId(UUID documentId) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("documentId", documentId);
    deleteBy(criteria);
  }

  /**
   * Deletes all chunks for a session (convenience method).
   *
   * @param sessionId the session ID
   */
  public void deleteBySessionId(UUID sessionId) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    deleteBy(criteria);
  }
}
