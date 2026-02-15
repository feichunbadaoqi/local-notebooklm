package com.flamingo.ai.notebooklm.elasticsearch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch index service for DocumentChunk documents.
 *
 * <p>This is a convenience wrapper around ElasticsearchIndexService that implements the generic
 * ElasticsearchIndexOperations interface and provides DocumentChunk-specific convenience methods.
 */
@Service
@Slf4j
public class DocumentChunkIndexService
    implements ElasticsearchIndexOperations<DocumentChunk, String> {

  private final ElasticsearchIndexService delegate;

  /** Production constructor - injects the existing ElasticsearchIndexService bean. */
  public DocumentChunkIndexService(ElasticsearchIndexService elasticsearchIndexService) {
    this.delegate = elasticsearchIndexService;
  }

  @Override
  public void initIndex() {
    delegate.initIndex();
  }

  @Override
  public void indexDocuments(List<DocumentChunk> documents) {
    delegate.indexChunks(documents);
  }

  @Override
  public List<DocumentChunk> vectorSearch(
      Map<String, Object> filterCriteria, List<Float> queryEmbedding, int topK) {
    UUID sessionId = (UUID) filterCriteria.get("sessionId");
    if (sessionId == null) {
      throw new IllegalArgumentException("sessionId filter is required for vector search");
    }
    return delegate.vectorSearch(sessionId, queryEmbedding, topK);
  }

  @Override
  public List<DocumentChunk> keywordSearch(
      Map<String, Object> filterCriteria, String query, int topK) {
    UUID sessionId = (UUID) filterCriteria.get("sessionId");
    if (sessionId == null) {
      throw new IllegalArgumentException("sessionId filter is required for keyword search");
    }
    return delegate.keywordSearch(sessionId, query, topK);
  }

  @Override
  public void deleteBy(Map<String, Object> criteria) {
    if (criteria.containsKey("documentId")) {
      delegate.deleteByDocumentId((UUID) criteria.get("documentId"));
    } else if (criteria.containsKey("sessionId")) {
      delegate.deleteBySessionId((UUID) criteria.get("sessionId"));
    } else {
      throw new IllegalArgumentException(
          "deleteBy requires either documentId or sessionId in criteria");
    }
  }

  @Override
  public void refresh() {
    delegate.refresh();
  }

  @Override
  public String getIndexName() {
    return "notebooklm-chunks"; // Matches default in ElasticsearchIndexService
  }

  // Convenience methods for backward compatibility

  /** Indexes document chunks (convenience method). */
  public void indexChunks(List<DocumentChunk> chunks) {
    indexDocuments(chunks);
  }

  /**
   * Performs vector search for a specific session (convenience method).
   *
   * @param sessionId the session ID
   * @param queryEmbedding the query embedding vector
   * @param topK number of results to return
   * @return list of matching document chunks
   */
  public List<DocumentChunk> vectorSearch(UUID sessionId, List<Float> queryEmbedding, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return vectorSearch(criteria, queryEmbedding, topK);
  }

  /**
   * Performs keyword search for a specific session (convenience method).
   *
   * @param sessionId the session ID
   * @param query the search query
   * @param topK number of results to return
   * @return list of matching document chunks
   */
  public List<DocumentChunk> keywordSearch(UUID sessionId, String query, int topK) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("sessionId", sessionId);
    return keywordSearch(criteria, query, topK);
  }

  /**
   * Performs vector search on a specific embedding field (Stage 2.2 - multiple embeddings).
   *
   * @param sessionId the session ID
   * @param embeddingField the field to search ("embedding", "titleEmbedding", or
   *     "contentEmbedding")
   * @param queryEmbedding the query embedding vector
   * @param topK number of results to return
   * @return list of matching document chunks
   */
  public List<DocumentChunk> vectorSearchByField(
      UUID sessionId, String embeddingField, List<Float> queryEmbedding, int topK) {
    return delegate.vectorSearchByField(sessionId, embeddingField, queryEmbedding, topK);
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
