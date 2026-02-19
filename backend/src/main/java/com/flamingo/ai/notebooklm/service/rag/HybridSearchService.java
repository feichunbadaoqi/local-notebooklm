package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunkIndexService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Hybrid search service combining vector search and BM25 keyword search using Reciprocal Rank
 * Fusion (RRF), with diversity-aware reranking for multi-document support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {

  private final DocumentChunkIndexService documentChunkIndexService;
  private final EmbeddingService embeddingService;
  private final DiversityReranker diversityReranker;
  private final LLMReranker llmReranker;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  /** Search result with intermediate retrieval steps for confidence calculation. */
  public record SearchResult(
      List<DocumentChunk> vectorResults,
      List<DocumentChunk> bm25Results,
      List<DocumentChunk> finalResults) {}

  /**
   * Performs hybrid search combining vector and keyword search with RRF fusion.
   *
   * @param sessionId the session to search within
   * @param query the search query
   * @param mode the interaction mode (determines number of results)
   * @return list of relevant document chunks
   */
  @Timed(value = "rag.search", description = "Time for hybrid search")
  public List<DocumentChunk> search(UUID sessionId, String query, InteractionMode mode) {
    return searchWithDetails(sessionId, query, mode).finalResults();
  }

  /**
   * Performs hybrid search and returns detailed results including intermediate steps. Used for
   * confidence calculation.
   *
   * @param sessionId the session to search within
   * @param query the search query
   * @param mode the interaction mode (determines number of results)
   * @return SearchResult with vector, BM25, and final results
   */
  @Timed(value = "rag.searchdetails", description = "Time for hybrid search with details")
  public SearchResult searchWithDetails(UUID sessionId, String query, InteractionMode mode) {
    log.debug("Starting hybrid search for session {} with query: {}", sessionId, query);
    int topK = mode.getRetrievalCount();
    int candidateMultiplier = ragConfig.getRetrieval().getCandidatesMultiplier();
    log.debug("Mode: {}, topK: {}, candidateMultiplier: {}", mode, topK, candidateMultiplier);

    // Get query embedding with query-specific prefix
    log.debug("Generating query embedding with instruction prefix...");
    List<Float> queryEmbedding = embeddingService.embedQuery(query);
    log.debug("Query embedding generated, size: {}", queryEmbedding.size());
    if (queryEmbedding.isEmpty()) {
      log.warn("Failed to generate query embedding, falling back to keyword search only");
      List<DocumentChunk> keywordOnly =
          documentChunkIndexService.keywordSearch(sessionId, query, topK);
      return new SearchResult(List.of(), keywordOnly, keywordOnly);
    }

    // Perform hybrid search using application-side RRF (BM25 + kNN, fused in Java).
    // Note: Elasticsearch's native RRF retriever requires a commercial license
    // (Platinum/Enterprise)
    // and is therefore not used here.
    log.debug("Performing vector search...");
    List<DocumentChunk> vectorResults =
        documentChunkIndexService.vectorSearch(
            sessionId, queryEmbedding, topK * candidateMultiplier);
    log.debug("Performing keyword search...");
    List<DocumentChunk> keywordResults =
        documentChunkIndexService.keywordSearch(sessionId, query, topK * candidateMultiplier);
    List<DocumentChunk> hybridResults =
        applyRrf(vectorResults, keywordResults, topK * candidateMultiplier);
    log.debug("Application-side RRF returned {} results", hybridResults.size());

    // Apply LLM reranking
    log.debug("Applying LLM reranking to {} candidates...", hybridResults.size());
    List<LLMReranker.ScoredChunk> rerankedScored =
        llmReranker.rerank(query, hybridResults, topK * 2);
    List<DocumentChunk> rerankedResults =
        rerankedScored.stream()
            .peek(scored -> scored.chunk().setRelevanceScore(scored.score()))
            .map(LLMReranker.ScoredChunk::chunk)
            .toList();
    log.debug("LLM reranking returned {} results", rerankedResults.size());

    // Apply diversity reranking for multi-document support
    List<DocumentChunk> diverseResults = diversityReranker.rerank(rerankedResults, topK);
    long uniqueDocs = diverseResults.stream().map(DocumentChunk::getDocumentId).distinct().count();
    log.debug(
        "Diversity reranking returned {} results from {} documents",
        diverseResults.size(),
        uniqueDocs);

    log.debug("Final diverse results:");
    for (int i = 0; i < diverseResults.size(); i++) {
      DocumentChunk chunk = diverseResults.get(i);
      log.debug(
          "  [{}] docId={} score={} file='{}' content='{}'",
          i,
          chunk.getDocumentId(),
          String.format("%.4f", chunk.getRelevanceScore()),
          chunk.getFileName(),
          chunk.getContent().length() > 100
              ? chunk.getContent().substring(0, 100) + "..."
              : chunk.getContent());
    }

    meterRegistry.counter("rag.search.success").increment();
    meterRegistry.gauge("rag.search.results", diverseResults, List::size);

    return new SearchResult(vectorResults, keywordResults, diverseResults);
  }

  /**
   * Applies Reciprocal Rank Fusion to combine results from multiple retrievers.
   *
   * <p>RRF score = Σ 1/(k + rank_i) for each retriever. This is the primary hybrid fusion strategy,
   * replacing Elasticsearch's native RRF retriever which requires a commercial license.
   */
  private List<DocumentChunk> applyRrf(
      List<DocumentChunk> vectorResults, List<DocumentChunk> keywordResults, int topK) {

    Map<String, Double> rrfScores = new HashMap<>();
    Map<String, DocumentChunk> chunkMap = new HashMap<>();
    int rrfK = ragConfig.getRetrieval().getRrfK();

    log.info(
        "[RRF] input: vectorResults={} keywordResults={} topK={} rrfK={}",
        vectorResults.size(),
        keywordResults.size(),
        topK,
        rrfK);

    // Score from vector search
    for (int i = 0; i < vectorResults.size(); i++) {
      DocumentChunk chunk = vectorResults.get(i);
      String id = chunk.getId();
      if (id == null) {
        log.warn(
            "[RRF] vectorResults[{}] has null id — all null-id chunks collapse to one key!", i);
      }
      double score = 1.0 / (rrfK + i + 1);
      rrfScores.merge(id, score, Double::sum);
      chunkMap.put(id, chunk);
    }

    // Score from keyword search
    for (int i = 0; i < keywordResults.size(); i++) {
      DocumentChunk chunk = keywordResults.get(i);
      String id = chunk.getId();
      if (id == null) {
        log.warn(
            "[RRF] keywordResults[{}] has null id — all null-id chunks collapse to one key!", i);
      }
      double score = 1.0 / (rrfK + i + 1);
      rrfScores.merge(id, score, Double::sum);
      chunkMap.putIfAbsent(id, chunk);
    }

    log.info("[RRF] unique chunks after merge: {}", rrfScores.size());

    // Sort by RRF score and return top K
    List<DocumentChunk> results =
        rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> chunkMap.get(entry.getKey()))
            .collect(Collectors.toList());

    log.info("[RRF] output: {} chunks (topK={})", results.size(), topK);
    return results;
  }

  /** Builds context string from retrieved chunks for LLM prompt. */
  public String buildContext(List<DocumentChunk> chunks) {
    if (chunks.isEmpty()) {
      return "";
    }

    StringBuilder context = new StringBuilder();
    context.append("=== DOCUMENT CONTEXT (from user's uploaded files) ===\n\n");
    context.append(
        "The following excerpts were retrieved from the user's documents. "
            + "Use this information ONLY if relevant to the user's question:\n\n");

    for (int i = 0; i < chunks.size(); i++) {
      DocumentChunk chunk = chunks.get(i);

      // Build source header with metadata
      StringBuilder sourceHeader = new StringBuilder();
      sourceHeader.append(String.format("[Source %d: %s", i + 1, chunk.getFileName()));

      // Include document title if different from filename
      String docTitle = chunk.getDocumentTitle();
      if (docTitle != null && !docTitle.isEmpty() && !docTitle.equals(chunk.getFileName())) {
        sourceHeader.append(" - ").append(docTitle);
      }

      // Include section title if available
      String sectionTitle = chunk.getSectionTitle();
      if (sectionTitle != null && !sectionTitle.isEmpty()) {
        sourceHeader.append(" > Section: ").append(sectionTitle);
      }

      sourceHeader.append("]\n");
      context.append(sourceHeader);
      context.append(chunk.getContent());
      context.append("\n\n");
    }

    context.append("=== END DOCUMENT CONTEXT ===");

    return context.toString();
  }
}
