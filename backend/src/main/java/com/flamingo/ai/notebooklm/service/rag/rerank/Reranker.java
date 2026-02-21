package com.flamingo.ai.notebooklm.service.rag.rerank;

import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import java.util.List;

/**
 * Abstraction for reranking document chunks by query relevance after initial retrieval.
 * Implementations may use cross-encoder models (TEI), LLM prompt-based scoring, or other
 * strategies.
 */
public interface Reranker {

  /**
   * Reranks candidates by relevance to the query and returns the top K results.
   *
   * @param query the search query
   * @param candidates candidate chunks from RRF fusion
   * @param topK number of results to return
   * @return top K reranked chunks with scores, sorted by score descending
   */
  List<ScoredChunk> rerank(String query, List<DocumentChunk> candidates, int topK);

  /** A document chunk paired with its relevance score. */
  record ScoredChunk(DocumentChunk chunk, double score) {}
}
