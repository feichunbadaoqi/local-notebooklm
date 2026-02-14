package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.agent.QueryReformulationAgent;
import com.flamingo.ai.notebooklm.agent.dto.QueryReformulationResult;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
  private final ChatMessageRepository chatMessageRepository;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  @Override
  @CircuitBreaker(name = "openai", fallbackMethod = "reformulateFallback")
  public String reformulate(UUID sessionId, String originalQuery, InteractionMode mode) {

    if (!ragConfig.getQueryReformulation().isEnabled()) {
      return originalQuery;
    }

    Timer.Sample sample = Timer.start(meterRegistry);

    try {
      // Retrieve conversation history
      int historyWindow = ragConfig.getQueryReformulation().getHistoryWindow();
      List<ChatMessage> recentMessages =
          chatMessageRepository.findRecentNonCompactedMessages(sessionId, historyWindow);

      if (recentMessages.isEmpty()) {
        log.debug("No conversation history for session {}, skipping reformulation", sessionId);
        return originalQuery;
      }

      // Build conversation history string
      String conversationHistory = buildConversationHistory(recentMessages);

      // Call AI agent
      QueryReformulationResult result = agent.reformulate(conversationHistory, originalQuery);

      // Log reasoning
      log.debug(
          "Query reformulation for session {}: needsReformulation={}, reasoning={}",
          sessionId,
          result.needsReformulation(),
          result.reasoning());

      // Metrics
      sample.stop(meterRegistry.timer("rag.query_reformulation.duration"));
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

  private String buildConversationHistory(List<ChatMessage> messages) {
    StringBuilder history = new StringBuilder();
    // Reverse to get chronological order (findRecentNonCompactedMessages returns DESC)
    for (int i = messages.size() - 1; i >= 0; i--) {
      ChatMessage msg = messages.get(i);
      String role = msg.getRole() == MessageRole.USER ? "User" : "Assistant";
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
