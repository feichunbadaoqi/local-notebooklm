package com.flamingo.ai.notebooklm.elasticsearch;

import java.util.List;
import java.util.Map;

/**
 * Generic interface for Elasticsearch index operations.
 *
 * @param <T> the document type stored in the index
 * @param <ID> the document ID type
 */
public interface ElasticsearchIndexOperations<T, ID> {

  /**
   * Initializes the index with appropriate mappings.
   *
   * <p>Creates the index if it doesn't exist, using the schema defined by the implementation.
   */
  void initIndex();

  /**
   * Indexes multiple documents in bulk.
   *
   * @param documents the documents to index
   */
  void indexDocuments(List<T> documents);

  /**
   * Performs vector similarity search with filters.
   *
   * @param filterCriteria key-value pairs for filtering (e.g., sessionId, documentId)
   * @param queryEmbedding the query vector
   * @param topK number of results to return
   * @return list of matching documents ordered by similarity
   */
  List<T> vectorSearch(Map<String, Object> filterCriteria, List<Float> queryEmbedding, int topK);

  /**
   * Performs keyword search with filters.
   *
   * @param filterCriteria key-value pairs for filtering (e.g., sessionId, documentId)
   * @param query the search query text
   * @param topK number of results to return
   * @return list of matching documents ordered by relevance
   */
  List<T> keywordSearch(Map<String, Object> filterCriteria, String query, int topK);

  /**
   * Deletes documents matching the given criteria.
   *
   * @param criteria key-value pairs for filtering documents to delete
   */
  void deleteBy(Map<String, Object> criteria);

  /**
   * Refreshes the index to make recent changes visible for search.
   *
   * <p>Useful after bulk indexing operations to ensure documents are immediately searchable.
   */
  void refresh();

  /**
   * Gets the name of the Elasticsearch index.
   *
   * @return the index name
   */
  String getIndexName();
}
