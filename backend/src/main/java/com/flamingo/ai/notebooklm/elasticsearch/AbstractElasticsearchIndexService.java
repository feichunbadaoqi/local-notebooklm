package com.flamingo.ai.notebooklm.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for Elasticsearch index services.
 *
 * <p>Provides common functionality for indexing, searching, and deleting documents with vector
 * embeddings. Subclasses define document-specific schema and conversion logic.
 *
 * @param <T> the document type stored in the index
 */
@Slf4j
public abstract class AbstractElasticsearchIndexService<T>
    implements ElasticsearchIndexOperations<T, String> {

  protected final ElasticsearchClient elasticsearchClient;
  protected final MeterRegistry meterRegistry;

  protected AbstractElasticsearchIndexService(
      ElasticsearchClient elasticsearchClient, MeterRegistry meterRegistry) {
    this.elasticsearchClient = elasticsearchClient;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Returns the Elasticsearch index name.
   *
   * @return the index name
   */
  @Override
  public abstract String getIndexName();

  /**
   * Returns the vector embedding dimensions.
   *
   * @return the vector dimensions (e.g., 1536 for text-embedding-ada-002, 3072 for
   *     text-embedding-3-small)
   */
  protected abstract int getVectorDimensions();

  /**
   * Defines the index properties (schema) for this document type.
   *
   * @return a map of field names to Elasticsearch property definitions
   */
  protected abstract Map<String, Property> defineIndexProperties();

  /**
   * Converts a document entity to an Elasticsearch document map.
   *
   * @param entity the entity to convert
   * @return the Elasticsearch document map
   */
  protected abstract Map<String, Object> convertToDocument(T entity);

  /**
   * Converts an Elasticsearch document map to a document entity.
   *
   * @param source the Elasticsearch document map
   * @return the document entity
   */
  protected abstract T convertFromDocument(Map<String, Object> source);

  /**
   * Extracts the document ID from the entity.
   *
   * @param entity the entity
   * @return the document ID
   */
  protected abstract String getDocumentId(T entity);

  /**
   * Builds the vector search request with filters.
   *
   * @param filterCriteria the filter criteria
   * @param queryEmbedding the query embedding
   * @param topK the number of results
   * @param embeddingField the embedding field name (default: "embedding")
   * @return the search request
   */
  protected abstract SearchRequest buildVectorSearchRequest(
      Map<String, Object> filterCriteria,
      List<Float> queryEmbedding,
      int topK,
      String embeddingField);

  /**
   * Builds the keyword search request with filters.
   *
   * @param filterCriteria the filter criteria
   * @param query the search query
   * @param topK the number of results
   * @return the search request
   */
  protected abstract SearchRequest buildKeywordSearchRequest(
      Map<String, Object> filterCriteria, String query, int topK);

  /**
   * Builds the delete by query request with criteria.
   *
   * @param criteria the delete criteria
   * @return the delete query
   */
  protected abstract Query buildDeleteQuery(Map<String, Object> criteria);

  /**
   * Returns the metric prefix for this index (e.g., "document_chunk", "memory", "chat_message").
   *
   * @return the metric prefix
   */
  protected abstract String getMetricPrefix();

  @PostConstruct
  @Override
  public void initIndex() {
    try {
      var indices = elasticsearchClient.indices();
      if (indices == null) {
        log.warn(
            "Elasticsearch client not available, skipping index initialization for {}",
            getIndexName());
        return;
      }
      boolean exists = indices.exists(e -> e.index(getIndexName())).value();
      if (!exists) {
        createIndex();
        log.info("Created Elasticsearch index: {}", getIndexName());
      }
    } catch (Exception e) {
      log.warn("Could not check/create Elasticsearch index {}: {}", getIndexName(), e.getMessage());
    }
  }

  private void createIndex() throws IOException {
    Map<String, Property> properties = defineIndexProperties();
    CreateIndexRequest request =
        CreateIndexRequest.of(c -> c.index(getIndexName()).mappings(m -> m.properties(properties)));
    elasticsearchClient.indices().create(request);
  }

  @Override
  @Timed(value = "elasticsearch.index", description = "Time to index documents")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "indexDocumentsFallback")
  public void indexDocuments(List<T> documents) {
    if (documents.isEmpty()) {
      return;
    }

    try {
      BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
      for (T document : documents) {
        String id = getDocumentId(document);
        Map<String, Object> docMap = convertToDocument(document);
        bulkBuilder.operations(
            op -> op.index(idx -> idx.index(getIndexName()).id(id).document(docMap)));
      }

      BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
      if (response.errors()) {
        log.warn("Some documents failed to index in {}: {}", getIndexName(), response.items());
        meterRegistry.counter(getMetricPrefix() + ".index.errors").increment();
      } else {
        log.debug("Indexed {} documents to {}", documents.size(), getIndexName());
        meterRegistry.counter(getMetricPrefix() + ".indexed").increment(documents.size());
      }
    } catch (IOException e) {
      log.error("Failed to index documents to {}: {}", getIndexName(), e.getMessage(), e);
      throw new RuntimeException("Failed to index documents", e);
    }
  }

  @SuppressWarnings("unused")
  private void indexDocumentsFallback(List<T> documents, Throwable t) {
    log.warn("{} indexing fallback triggered: {}", getIndexName(), t.getMessage());
    meterRegistry.counter(getMetricPrefix() + ".index.fallback").increment();
  }

  @Override
  @Timed(value = "elasticsearch.vector_search", description = "Time for vector search")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "vectorSearchFallback")
  public List<T> vectorSearch(
      Map<String, Object> filterCriteria, List<Float> queryEmbedding, int topK) {
    return vectorSearchByField(filterCriteria, "embedding", queryEmbedding, topK);
  }

  /**
   * Vector search on a specific embedding field (for multi-embedding support).
   *
   * @param filterCriteria the filter criteria
   * @param embeddingField the embedding field name
   * @param queryEmbedding the query embedding
   * @param topK the number of results
   * @return list of matching documents
   */
  @Timed(
      value = "elasticsearch.vector_search_by_field",
      description = "Time for vector search by field")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "vectorSearchFallback")
  public List<T> vectorSearchByField(
      Map<String, Object> filterCriteria,
      String embeddingField,
      List<Float> queryEmbedding,
      int topK) {
    try {
      SearchRequest request =
          buildVectorSearchRequest(filterCriteria, queryEmbedding, topK, embeddingField);
      SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
      List<T> results = mapHitsToDocuments(response.hits().hits());
      meterRegistry.counter(getMetricPrefix() + ".vector_search").increment();
      return results;
    } catch (IOException e) {
      log.error("Vector search failed for {}: {}", getIndexName(), e.getMessage(), e);
      throw new RuntimeException("Vector search failed", e);
    }
  }

  @SuppressWarnings("unused")
  private List<T> vectorSearchFallback(
      Map<String, Object> filterCriteria, List<Float> queryEmbedding, int topK, Throwable t) {
    log.warn("{} vector search fallback triggered: {}", getIndexName(), t.getMessage());
    meterRegistry.counter(getMetricPrefix() + ".vector_search.fallback").increment();
    return List.of();
  }

  @SuppressWarnings("unused")
  private List<T> vectorSearchFallback(
      Map<String, Object> filterCriteria,
      String embeddingField,
      List<Float> queryEmbedding,
      int topK,
      Throwable t) {
    log.warn("{} vector search by field fallback triggered: {}", getIndexName(), t.getMessage());
    meterRegistry.counter(getMetricPrefix() + ".vector_search.fallback").increment();
    return List.of();
  }

  @Override
  @Timed(value = "elasticsearch.keyword_search", description = "Time for keyword search")
  @CircuitBreaker(name = "elasticsearch", fallbackMethod = "keywordSearchFallback")
  public List<T> keywordSearch(Map<String, Object> filterCriteria, String query, int topK) {
    try {
      SearchRequest request = buildKeywordSearchRequest(filterCriteria, query, topK);
      SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
      List<T> results = mapHitsToDocuments(response.hits().hits());
      meterRegistry.counter(getMetricPrefix() + ".keyword_search").increment();
      return results;
    } catch (IOException e) {
      log.error("Keyword search failed for {}: {}", getIndexName(), e.getMessage(), e);
      throw new RuntimeException("Keyword search failed", e);
    }
  }

  @SuppressWarnings("unused")
  private List<T> keywordSearchFallback(
      Map<String, Object> filterCriteria, String query, int topK, Throwable t) {
    log.warn("{} keyword search fallback triggered: {}", getIndexName(), t.getMessage());
    meterRegistry.counter(getMetricPrefix() + ".keyword_search.fallback").increment();
    return List.of();
  }

  @Override
  @Timed(value = "elasticsearch.delete_by", description = "Time to delete documents by criteria")
  public void deleteBy(Map<String, Object> criteria) {
    try {
      Query deleteQuery = buildDeleteQuery(criteria);
      DeleteByQueryRequest request =
          DeleteByQueryRequest.of(d -> d.index(getIndexName()).query(deleteQuery));
      elasticsearchClient.deleteByQuery(request);
      log.info("Deleted documents from {} with criteria: {}", getIndexName(), criteria);
      meterRegistry.counter(getMetricPrefix() + ".deleted").increment();
    } catch (IOException e) {
      log.error(
          "Failed to delete documents from {} with criteria {}: {}",
          getIndexName(),
          criteria,
          e.getMessage(),
          e);
      throw new RuntimeException("Failed to delete documents", e);
    }
  }

  @Override
  public void refresh() {
    try {
      elasticsearchClient.indices().refresh(r -> r.index(getIndexName()));
      log.debug("Refreshed index: {}", getIndexName());
    } catch (IOException e) {
      log.warn("Failed to refresh index {}: {}", getIndexName(), e.getMessage());
    }
  }

  private List<T> mapHitsToDocuments(List<Hit<Map>> hits) {
    List<T> documents = new ArrayList<>();
    for (Hit<Map> hit : hits) {
      Map<String, Object> source = hit.source();
      if (source != null) {
        T document = convertFromDocument(source);
        // Set relevance score if the document supports it
        if (document instanceof ScoredDocument && hit.score() != null) {
          ((ScoredDocument) document).setRelevanceScore(hit.score());
        }
        documents.add(document);
      }
    }
    return documents;
  }

  /** Marker interface for documents that support relevance scoring. */
  public interface ScoredDocument {
    void setRelevanceScore(Double score);
  }
}
