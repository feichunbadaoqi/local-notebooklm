package com.flamingo.ai.notebooklm.service.memory;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.elasticsearch.MemoryDocument;
import com.flamingo.ai.notebooklm.elasticsearch.MemoryIndexService;
import com.flamingo.ai.notebooklm.service.rag.embedding.EmbeddingService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Hybrid search service for memories combining vector search, BM25 keyword search, and importance
 * scoring.
 *
 * <p>Uses Reciprocal Rank Fusion (RRF) to combine vector and keyword search results, then applies
 * hybrid scoring that combines semantic relevance with memory importance (configurable weights).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryHybridSearchService {

  private final MemoryIndexService memoryIndexService;
  private final EmbeddingService embeddingService;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  private static final int RRF_K = 60;

  /**
   * Searches memories using hybrid search with importance scoring.
   *
   * @param sessionId the session ID to search within
   * @param query the search query
   * @param limit maximum number of memories to return
   * @return list of relevant memories ordered by hybrid score
   */
  @Timed(value = "memory.search", description = "Time for hybrid search on memories")
  public List<MemoryDocument> search(UUID sessionId, String query, int limit) {
    log.debug("Starting memory hybrid search for session {} with query: {}", sessionId, query);

    // Get candidate pool multiplier from config
    int candidateMultiplier =
        ragConfig.getMemory() != null && ragConfig.getMemory().getCandidatePoolMultiplier() != null
            ? ragConfig.getMemory().getCandidatePoolMultiplier()
            : 3;
    int candidateLimit = limit * candidateMultiplier;

    // Get query embedding
    List<Float> queryEmbedding = embeddingService.embedQuery(query);
    if (queryEmbedding.isEmpty()) {
      log.warn("Failed to generate query embedding, falling back to keyword search only");
      List<MemoryDocument> keywordOnly = memoryIndexService.keywordSearch(sessionId, query, limit);
      return applyImportanceBoost(keywordOnly, limit);
    }

    // Perform vector search
    List<MemoryDocument> vectorResults =
        memoryIndexService.vectorSearch(sessionId, queryEmbedding, candidateLimit);
    log.debug("Vector search returned {} memories", vectorResults.size());

    // Perform keyword search (BM25)
    List<MemoryDocument> keywordResults =
        memoryIndexService.keywordSearch(sessionId, query, candidateLimit);
    log.debug("Keyword search returned {} memories", keywordResults.size());

    // Apply RRF fusion
    List<MemoryDocument> fusedResults = applyRrf(vectorResults, keywordResults);
    log.debug("RRF fusion returned {} memories", fusedResults.size());

    // Apply hybrid scoring (semantic relevance + importance)
    List<MemoryDocument> finalResults = applyImportanceBoost(fusedResults, limit);
    log.debug("Hybrid scoring returned {} memories", finalResults.size());

    meterRegistry.counter("memory.search").increment();
    return finalResults;
  }

  /**
   * Applies Reciprocal Rank Fusion to combine vector and keyword search results.
   *
   * @param vectorResults results from vector search
   * @param keywordResults results from keyword search
   * @return fused results ordered by RRF score
   */
  private List<MemoryDocument> applyRrf(
      List<MemoryDocument> vectorResults, List<MemoryDocument> keywordResults) {

    Map<String, Double> rrfScores = new HashMap<>();
    Map<String, MemoryDocument> memoryMap = new HashMap<>();

    // Add vector search results with rank
    for (int i = 0; i < vectorResults.size(); i++) {
      MemoryDocument memory = vectorResults.get(i);
      double score = 1.0 / (RRF_K + i + 1);
      rrfScores.merge(memory.getId(), score, Double::sum);
      memoryMap.putIfAbsent(memory.getId(), memory);
    }

    // Add keyword search results with rank
    for (int i = 0; i < keywordResults.size(); i++) {
      MemoryDocument memory = keywordResults.get(i);
      double score = 1.0 / (RRF_K + i + 1);
      rrfScores.merge(memory.getId(), score, Double::sum);
      memoryMap.putIfAbsent(memory.getId(), memory);
    }

    // Sort by RRF score and update relevanceScore
    return rrfScores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .map(
            entry -> {
              MemoryDocument memory = memoryMap.get(entry.getKey());
              memory.setRelevanceScore(entry.getValue());
              return memory;
            })
        .collect(Collectors.toList());
  }

  /**
   * Applies hybrid scoring combining semantic relevance and memory importance.
   *
   * <p>Formula: hybrid_score = (relevance_score * semantic_weight) + (importance * (1 -
   * semantic_weight))
   *
   * @param memories memories with RRF relevance scores
   * @param topK number of results to return
   * @return top-K memories ordered by hybrid score
   */
  private List<MemoryDocument> applyImportanceBoost(List<MemoryDocument> memories, int topK) {
    // Get semantic weight from config (default 0.7 = 70% semantic, 30% importance)
    float semanticWeight =
        ragConfig.getMemory() != null && ragConfig.getMemory().getSemanticWeight() != null
            ? ragConfig.getMemory().getSemanticWeight()
            : 0.7f;

    float importanceWeight = 1.0f - semanticWeight;

    // Normalize relevance scores to 0-1 range
    double maxRelevance =
        memories.stream().mapToDouble(MemoryDocument::getRelevanceScore).max().orElse(1.0);

    return memories.stream()
        .map(
            memory -> {
              double normalizedRelevance =
                  maxRelevance > 0 ? memory.getRelevanceScore() / maxRelevance : 0.0;
              float importance = memory.getImportance() != null ? memory.getImportance() : 0.0f;

              // Hybrid score: semantic relevance (70%) + importance (30%)
              double hybridScore =
                  (normalizedRelevance * semanticWeight) + (importance * importanceWeight);

              memory.setRelevanceScore(hybridScore);
              return memory;
            })
        .sorted(Comparator.comparing(MemoryDocument::getRelevanceScore).reversed())
        .limit(topK)
        .collect(Collectors.toList());
  }
}
