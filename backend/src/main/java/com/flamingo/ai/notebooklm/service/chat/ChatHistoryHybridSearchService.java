package com.flamingo.ai.notebooklm.service.chat;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.elasticsearch.ChatMessageDocument;
import com.flamingo.ai.notebooklm.elasticsearch.ChatMessageIndexService;
import com.flamingo.ai.notebooklm.service.rag.EmbeddingService;
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
 * Hybrid search service for chat history combining vector search and BM25 keyword search using
 * Reciprocal Rank Fusion (RRF).
 *
 * <p>Used for finding relevant past chat messages for query reformulation and context enrichment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryHybridSearchService {

  private final ChatMessageIndexService chatMessageIndexService;
  private final EmbeddingService embeddingService;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  private static final int RRF_K = 60;

  /**
   * Searches chat history for relevant messages.
   *
   * @param sessionId the session ID to search within
   * @param query the search query
   * @param limit maximum number of messages to return
   * @return list of relevant chat messages ordered by relevance
   */
  @Timed(value = "chat_history.search", description = "Time for hybrid search on chat history")
  public List<ChatMessageDocument> search(UUID sessionId, String query, int limit) {
    log.debug(
        "Starting chat history hybrid search for session {} with query: {}", sessionId, query);

    int candidateMultiplier =
        ragConfig.getQueryReformulation() != null
                && ragConfig.getQueryReformulation().getCandidatePoolMultiplier() != null
            ? ragConfig.getQueryReformulation().getCandidatePoolMultiplier()
            : 4;
    int candidateLimit = limit * candidateMultiplier;

    // Get query embedding
    List<Float> queryEmbedding = embeddingService.embedQuery(query);
    if (queryEmbedding.isEmpty()) {
      log.warn("Failed to generate query embedding, falling back to keyword search only");
      List<ChatMessageDocument> keywordOnly =
          chatMessageIndexService.keywordSearch(sessionId, query, limit);
      return keywordOnly;
    }

    // Perform vector search
    List<ChatMessageDocument> vectorResults =
        chatMessageIndexService.vectorSearch(sessionId, queryEmbedding, candidateLimit);
    log.debug("Vector search returned {} chat messages", vectorResults.size());

    // Perform keyword search (BM25)
    List<ChatMessageDocument> keywordResults =
        chatMessageIndexService.keywordSearch(sessionId, query, candidateLimit);
    log.debug("Keyword search returned {} chat messages", keywordResults.size());

    // Apply RRF fusion
    List<ChatMessageDocument> fusedResults = applyRrf(vectorResults, keywordResults, limit);
    log.debug("RRF fusion returned {} chat messages", fusedResults.size());

    meterRegistry.counter("chat_history.search").increment();
    return fusedResults;
  }

  /**
   * Applies Reciprocal Rank Fusion to combine vector and keyword search results.
   *
   * @param vectorResults results from vector search
   * @param keywordResults results from keyword search
   * @param topK number of results to return
   * @return fused results ordered by RRF score
   */
  private List<ChatMessageDocument> applyRrf(
      List<ChatMessageDocument> vectorResults, List<ChatMessageDocument> keywordResults, int topK) {

    Map<String, Double> rrfScores = new HashMap<>();
    Map<String, ChatMessageDocument> messageMap = new HashMap<>();

    // Add vector search results with rank
    for (int i = 0; i < vectorResults.size(); i++) {
      ChatMessageDocument message = vectorResults.get(i);
      double score = 1.0 / (RRF_K + i + 1);
      rrfScores.merge(message.getId(), score, Double::sum);
      messageMap.putIfAbsent(message.getId(), message);
    }

    // Add keyword search results with rank
    for (int i = 0; i < keywordResults.size(); i++) {
      ChatMessageDocument message = keywordResults.get(i);
      double score = 1.0 / (RRF_K + i + 1);
      rrfScores.merge(message.getId(), score, Double::sum);
      messageMap.putIfAbsent(message.getId(), message);
    }

    // Sort by RRF score and return top-K
    return rrfScores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(topK)
        .map(
            entry -> {
              ChatMessageDocument message = messageMap.get(entry.getKey());
              message.setRelevanceScore(entry.getValue());
              return message;
            })
        .collect(Collectors.toList());
  }
}
