package com.flamingo.ai.notebooklm.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Value("${app.elasticsearch.text-search-analyzer:standard}")
  private String textSearchAnalyzer;

  @Autowired
  public DocumentChunkIndexService(
      ElasticsearchClient elasticsearchClient, MeterRegistry meterRegistry) {
    super(elasticsearchClient, meterRegistry);
  }

  /** Constructor for testing - allows setting index name and vector dimensions. */
  @VisibleForTesting
  public DocumentChunkIndexService(
      ElasticsearchClient elasticsearchClient,
      MeterRegistry meterRegistry,
      String indexName,
      int vectorDimensions) {
    super(elasticsearchClient, meterRegistry);
    this.indexName = indexName;
    this.vectorDimensions = vectorDimensions;
    this.textAnalyzer = "standard"; // Default for tests
    this.textSearchAnalyzer = "standard"; // Default for tests
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
    log.info(
        "Creating index '{}' with text analyzer: {}, search analyzer: {}",
        indexName,
        textAnalyzer,
        textSearchAnalyzer);
    if (!"standard".equals(textAnalyzer)
        && !"ik_max_word".equals(textAnalyzer)
        && !"ik_smart".equals(textAnalyzer)) {
      log.warn("Using custom analyzer '{}'. Ensure it's installed in Elasticsearch.", textAnalyzer);
    }

    Map<String, Property> properties = new HashMap<>();
    // IMPORTANT: documentId and sessionId MUST be keyword type for exact matching
    properties.put("documentId", Property.of(p -> p.keyword(k -> k)));
    properties.put("sessionId", Property.of(p -> p.keyword(k -> k)));
    properties.put("fileName", Property.of(p -> p.text(TextProperty.of(t -> t))));
    properties.put("chunkIndex", Property.of(p -> p.integer(i -> i)));
    properties.put(
        "content",
        Property.of(
            p ->
                p.text(
                    TextProperty.of(
                        t -> t.analyzer(textAnalyzer).searchAnalyzer(textSearchAnalyzer)))));
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
        "documentTitle",
        Property.of(
            p ->
                p.text(
                    TextProperty.of(
                        t -> t.analyzer(textAnalyzer).searchAnalyzer(textSearchAnalyzer)))));
    properties.put(
        "sectionTitle",
        Property.of(
            p ->
                p.text(
                    TextProperty.of(
                        t -> t.analyzer(textAnalyzer).searchAnalyzer(textSearchAnalyzer)))));
    // keywords is an array of tags (["kusto", "gpu", "oversubscription"]), not free-form text
    properties.put("keywords", Property.of(p -> p.keyword(k -> k)));
    properties.put(
        "enrichedContent",
        Property.of(
            p ->
                p.text(
                    TextProperty.of(
                        t -> t.analyzer(textAnalyzer).searchAnalyzer(textSearchAnalyzer)))));

    // Structure-aware chunking fields (Phase 2)
    properties.put("sectionBreadcrumb", Property.of(p -> p.keyword(k -> k)));
    properties.put("associatedImageIds", Property.of(p -> p.keyword(k -> k)));

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
    // Structure-aware chunking fields
    if (chunk.getSectionBreadcrumb() != null && !chunk.getSectionBreadcrumb().isEmpty()) {
      document.put("sectionBreadcrumb", chunk.getSectionBreadcrumb());
    }
    if (chunk.getAssociatedImageIds() != null && !chunk.getAssociatedImageIds().isEmpty()) {
      document.put("associatedImageIds", chunk.getAssociatedImageIds());
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
    // Structure-aware chunking fields
    if (source.get("sectionBreadcrumb") != null) {
      builder.sectionBreadcrumb((List<String>) source.get("sectionBreadcrumb"));
    }
    if (source.get("associatedImageIds") != null) {
      builder.associatedImageIds((List<String>) source.get("associatedImageIds"));
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
                                                            // "documentTitle^3.0", // 3x boost for
                                                            // title
                                                            // "sectionTitle^2.0", // 2x boost for
                                                            // section
                                                            // "fileName^1.5", // 1.5x boost for
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

  /** Convenience method: vector search filtered by session. */
  public List<DocumentChunk> vectorSearch(UUID sessionId, List<Float> queryEmbedding, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return vectorSearch(criteria, queryEmbedding, topK);
  }

  /** Convenience method: BM25 keyword search filtered by session. */
  public List<DocumentChunk> keywordSearch(UUID sessionId, String query, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return keywordSearch(criteria, query, topK);
  }

  /** Convenience method: vector search on a specific embedding field, filtered by session. */
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
