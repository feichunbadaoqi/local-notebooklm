package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.agent.QueryReformulationAgent;
import com.flamingo.ai.notebooklm.agent.dto.QueryReformulationResult;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.elasticsearch.ChatMessageDocument;
import com.flamingo.ai.notebooklm.service.chat.ChatHistoryHybridSearchService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryReformulationServiceImpl implements QueryReformulationService {

  private final QueryReformulationAgent agent;
  private final ChatHistoryHybridSearchService chatHistoryHybridSearchService;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  @Override
  @Timed(value = "rag.query_reformulation", description = "Time to reformulate query")
  @CircuitBreaker(name = "openai", fallbackMethod = "reformulateFallback")
  public String reformulate(UUID sessionId, String originalQuery, InteractionMode mode) {

    if (!ragConfig.getQueryReformulation().isEnabled()) {
      return originalQuery;
    }

    try {
      // Retrieve semantically relevant conversation history using hybrid search
      int historyWindow = ragConfig.getQueryReformulation().getHistoryWindow();
      List<ChatMessageDocument> relevantMessages =
          chatHistoryHybridSearchService.search(sessionId, originalQuery, historyWindow);

      if (relevantMessages.isEmpty()) {
        log.debug("No conversation history for session {}, skipping reformulation", sessionId);
        return originalQuery;
      }

      // Build conversation history string from relevant messages
      String conversationHistory = buildConversationHistoryFromDocuments(relevantMessages);
      log.debug(
          "Using {} semantically relevant messages for query reformulation",
          relevantMessages.size());

      // Call AI agent
      QueryReformulationResult result = agent.reformulate(conversationHistory, originalQuery);

      // Log reasoning
      log.debug(
          "Query reformulation for session {}: needsReformulation={}, reasoning={}",
          sessionId,
          result.needsReformulation(),
          result.reasoning());

      // Metrics
      if (result.needsReformulation()) {
        meterRegistry.counter("rag.query_reformulation.reformulated").increment();
      } else {
        meterRegistry.counter("rag.query_reformulation.standalone").increment();
      }

      // Validate result
      String finalQuery = validateQuery(result.query(), originalQuery);

      if (!finalQuery.equals(originalQuery)) {
        log.info(
            "Reformulated query for session {}: '{}' → '{}'", sessionId, originalQuery, finalQuery);
      }

      return finalQuery;

    } catch (Exception e) {
      log.error("Query reformulation failed for session {}: {}", sessionId, e.getMessage());
      meterRegistry.counter("rag.query_reformulation.errors").increment();
      return originalQuery;
    }
  }

  @SuppressWarnings("unused")
  private String reformulateFallback(
      UUID sessionId, String originalQuery, InteractionMode mode, Throwable t) {
    log.warn(
        "Query reformulation fallback triggered for session {}: {}", sessionId, t.getMessage());
    return originalQuery;
  }

  private String buildConversationHistoryFromDocuments(List<ChatMessageDocument> messages) {
    StringBuilder history = new StringBuilder();
    // Sort by timestamp to get chronological order
    List<ChatMessageDocument> sorted =
        messages.stream()
            .sorted((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
            .toList();

    for (ChatMessageDocument msg : sorted) {
      String role = "USER".equals(msg.getRole()) ? "User" : "Assistant";
      history.append(role).append(": ").append(msg.getContent()).append("\n");
    }
    return history.toString().trim();
  }

  private String validateQuery(String reformulatedQuery, String originalQuery) {
    if (reformulatedQuery == null || reformulatedQuery.isBlank()) {
      log.warn("Reformulated query is empty, using original");
      return originalQuery;
    }

    int maxLength = ragConfig.getQueryReformulation().getMaxQueryLength();
    if (reformulatedQuery.length() > maxLength) {
      log.warn("Reformulated query too long ({}), truncating", reformulatedQuery.length());
      return reformulatedQuery.substring(0, maxLength);
    }

    return reformulatedQuery;
  }
}
