package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.elasticsearch.ElasticsearchIndexService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
 * Fusion (RRF).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {

  private final ElasticsearchIndexService elasticsearchIndexService;
  private final EmbeddingService embeddingService;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  private static final int RRF_K = 60;

  /**
   * Performs hybrid search combining vector and keyword search with RRF fusion.
   *
   * @param sessionId the session to search within
   * @param query the search query
   * @param mode the interaction mode (determines number of results)
   * @return list of relevant document chunks
   */
  public List<DocumentChunk> search(UUID sessionId, String query, InteractionMode mode) {
    log.debug("Starting hybrid search for session {} with query: {}", sessionId, query);
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      int topK = mode.getRetrievalCount();
      int candidateMultiplier = 2;
      log.debug("Mode: {}, topK: {}", mode, topK);

      // Get query embedding
      log.debug("Generating query embedding...");
      List<Float> queryEmbedding = embeddingService.embedText(query);
      log.debug("Query embedding generated, size: {}", queryEmbedding.size());
      if (queryEmbedding.isEmpty()) {
        log.warn("Failed to generate query embedding, falling back to keyword search only");
        return elasticsearchIndexService.keywordSearch(sessionId, query, topK);
      }

      // Perform both searches in parallel conceptually (sequential here for simplicity)
      log.debug("Performing vector search...");
      List<DocumentChunk> vectorResults =
          elasticsearchIndexService.vectorSearch(
              sessionId, queryEmbedding, topK * candidateMultiplier);
      log.debug("Vector search returned {} results", vectorResults.size());

      log.debug("Performing keyword search...");
      List<DocumentChunk> keywordResults =
          elasticsearchIndexService.keywordSearch(sessionId, query, topK * candidateMultiplier);
      log.debug("Keyword search returned {} results", keywordResults.size());

      // Apply RRF fusion
      List<DocumentChunk> fusedResults = applyRrf(vectorResults, keywordResults, topK);
      log.debug("RRF fusion returned {} results", fusedResults.size());

      meterRegistry.counter("rag.search.success").increment();
      meterRegistry.gauge("rag.search.results", fusedResults, List::size);

      return fusedResults;
    } finally {
      sample.stop(meterRegistry.timer("rag.search.duration"));
    }
  }

  /**
   * Applies Reciprocal Rank Fusion to combine results from multiple retrievers. RRF score = Σ 1/(k
   * + rank_i) for each retriever
   */
  private List<DocumentChunk> applyRrf(
      List<DocumentChunk> vectorResults, List<DocumentChunk> keywordResults, int topK) {

    Map<String, Double> rrfScores = new HashMap<>();
    Map<String, DocumentChunk> chunkMap = new HashMap<>();

    // Score from vector search
    for (int i = 0; i < vectorResults.size(); i++) {
      DocumentChunk chunk = vectorResults.get(i);
      String id = chunk.getId();
      double score = 1.0 / (RRF_K + i + 1);
      rrfScores.merge(id, score, Double::sum);
      chunkMap.put(id, chunk);
    }

    // Score from keyword search
    for (int i = 0; i < keywordResults.size(); i++) {
      DocumentChunk chunk = keywordResults.get(i);
      String id = chunk.getId();
      double score = 1.0 / (RRF_K + i + 1);
      rrfScores.merge(id, score, Double::sum);
      chunkMap.putIfAbsent(id, chunk);
    }

    // Sort by RRF score and return top K
    return rrfScores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(topK)
        .map(entry -> chunkMap.get(entry.getKey()))
        .collect(Collectors.toList());
  }

  /** Builds context string from retrieved chunks for LLM prompt. */
  public String buildContext(List<DocumentChunk> chunks) {
    if (chunks.isEmpty()) {
      return "";
    }

    StringBuilder context = new StringBuilder();
    context.append("Relevant information from your documents:\n\n");

    for (int i = 0; i < chunks.size(); i++) {
      DocumentChunk chunk = chunks.get(i);
      context.append(String.format("[Source %d: %s]\n", i + 1, chunk.getFileName()));
      context.append(chunk.getContent());
      context.append("\n\n");
    }

    return context.toString();
  }
}
