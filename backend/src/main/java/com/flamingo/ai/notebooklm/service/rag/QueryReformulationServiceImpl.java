package com.flamingo.ai.notebooklm.service.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flamingo.ai.notebooklm.agent.QueryReformulationAgent;
import com.flamingo.ai.notebooklm.agent.dto.QueryReformulationResult;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.elasticsearch.ChatMessageDocument;
import com.flamingo.ai.notebooklm.service.chat.ChatHistoryHybridSearchService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryReformulationServiceImpl implements QueryReformulationService {

  private final QueryReformulationAgent agent;
  private final ChatHistoryHybridSearchService chatHistoryHybridSearchService;
  private final ChatMessageRepository chatMessageRepository;
  private final ObjectMapper objectMapper;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  @Override
  @Timed(value = "rag.query_reformulation", description = "Time to reformulate query")
  @CircuitBreaker(name = "openai", fallbackMethod = "reformulateFallback")
  public ReformulatedQuery reformulate(UUID sessionId, String originalQuery, InteractionMode mode) {

    if (!ragConfig.getQueryReformulation().isEnabled()) {
      return new ReformulatedQuery(originalQuery, false, List.of());
    }

    try {
      int minRecent = ragConfig.getQueryReformulation().getMinRecentMessages();
      int historyWindow = ragConfig.getQueryReformulation().getHistoryWindow();

      // Always fetch the most recent messages from DB for recency-biased context
      List<ChatMessage> recentDbMessages =
          chatMessageRepository.findRecentMessages(sessionId, Pageable.ofSize(minRecent)).stream()
              .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt())) // chronological order
              .toList();

      // Fetch semantically relevant messages via hybrid search
      List<ChatMessageDocument> semanticMessages =
          chatHistoryHybridSearchService.search(sessionId, originalQuery, historyWindow);

      // Merge: recent DB messages first (by ID), then fill with semantic results (deduplicated)
      List<ChatMessageDocument> mergedMessages =
          mergeMessages(recentDbMessages, semanticMessages, historyWindow);

      if (mergedMessages.isEmpty() && recentDbMessages.isEmpty()) {
        log.debug("No conversation history for session {}, skipping reformulation", sessionId);
        return new ReformulatedQuery(originalQuery, false, List.of());
      }

      // Format the most recent exchange (last USER + ASSISTANT from DB) for the agent
      String recentExchange = buildRecentExchange(recentDbMessages);

      // Build broader conversation history from merged messages
      String conversationHistory = buildConversationHistoryFromDocuments(mergedMessages);

      // Extract anchor document IDs from the last ASSISTANT message's retrievedContextJson
      List<String> anchorDocumentIds = extractAnchorDocumentIds(recentDbMessages);

      log.debug(
          "Reformulation context: recentMessages={}, mergedHistory={}, anchorDocs={}",
          recentDbMessages.size(),
          mergedMessages.size(),
          anchorDocumentIds.size());

      // Call AI agent with explicit recent exchange and broader history
      QueryReformulationResult result =
          agent.reformulate(recentExchange, conversationHistory, originalQuery);

      // Log reasoning
      log.debug(
          "Query reformulation for session {}: needsReformulation={}, isFollowUp={}, reasoning={}",
          sessionId,
          result.needsReformulation(),
          result.isFollowUp(),
          result.reasoning());

      // Metrics
      if (result.needsReformulation()) {
        meterRegistry.counter("rag.query_reformulation.reformulated").increment();
      } else {
        meterRegistry.counter("rag.query_reformulation.standalone").increment();
      }
      if (result.isFollowUp()) {
        meterRegistry.counter("rag.query_reformulation.followup").increment();
      }

      // Validate result
      String finalQuery = validateQuery(result.query(), originalQuery);

      if (!finalQuery.equals(originalQuery)) {
        log.info(
            "Reformulated query for session {}: '{}' → '{}'", sessionId, originalQuery, finalQuery);
      }

      List<String> anchors = result.isFollowUp() ? anchorDocumentIds : List.of();
      return new ReformulatedQuery(finalQuery, result.isFollowUp(), anchors);

    } catch (Exception e) {
      log.error("Query reformulation failed for session {}: {}", sessionId, e.getMessage());
      meterRegistry.counter("rag.query_reformulation.errors").increment();
      return new ReformulatedQuery(originalQuery, false, List.of());
    }
  }

  @SuppressWarnings("unused")
  private ReformulatedQuery reformulateFallback(
      UUID sessionId, String originalQuery, InteractionMode mode, Throwable t) {
    log.warn(
        "Query reformulation fallback triggered for session {}: {}", sessionId, t.getMessage());
    return new ReformulatedQuery(originalQuery, false, List.of());
  }

  /**
   * Merges recent DB messages with semantic search results. Recent DB messages take priority;
   * semantic results fill the remainder up to historyWindow (deduplicated by ID).
   */
  private List<ChatMessageDocument> mergeMessages(
      List<ChatMessage> recentDbMessages,
      List<ChatMessageDocument> semanticMessages,
      int historyWindow) {

    // Convert recent DB messages to ChatMessageDocument for uniform handling
    Set<String> includedIds = new LinkedHashSet<>();
    List<ChatMessageDocument> merged = new ArrayList<>();

    for (ChatMessage msg : recentDbMessages) {
      String id = msg.getId().toString();
      if (includedIds.add(id)) {
        merged.add(toChatMessageDocument(msg));
      }
    }

    // Fill remainder with semantic results (deduplicated)
    for (ChatMessageDocument doc : semanticMessages) {
      if (merged.size() >= historyWindow) {
        break;
      }
      if (includedIds.add(doc.getId())) {
        merged.add(doc);
      }
    }

    // Sort by timestamp for chronological display
    merged.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
    return merged;
  }

  /** Formats the most recent USER + ASSISTANT exchange from recent DB messages. */
  private String buildRecentExchange(List<ChatMessage> recentDbMessages) {
    if (recentDbMessages.isEmpty()) {
      return "(no prior exchange)";
    }
    StringBuilder sb = new StringBuilder();
    for (ChatMessage msg : recentDbMessages) {
      String role = msg.getRole() == MessageRole.USER ? "User" : "Assistant";
      sb.append(role).append(": ").append(msg.getContent()).append("\n");
    }
    return sb.toString().trim();
  }

  private String buildConversationHistoryFromDocuments(List<ChatMessageDocument> messages) {
    if (messages.isEmpty()) {
      return "(no conversation history)";
    }
    StringBuilder history = new StringBuilder();
    for (ChatMessageDocument msg : messages) {
      String role = "USER".equals(msg.getRole()) ? "User" : "Assistant";
      history.append(role).append(": ").append(msg.getContent()).append("\n");
    }
    return history.toString().trim();
  }

  /**
   * Parses the last ASSISTANT message's retrievedContextJson to extract document IDs for source
   * anchoring.
   */
  private List<String> extractAnchorDocumentIds(List<ChatMessage> recentDbMessages) {
    for (int i = recentDbMessages.size() - 1; i >= 0; i--) {
      ChatMessage msg = recentDbMessages.get(i);
      if (msg.getRole() == MessageRole.ASSISTANT
          && msg.getRetrievedContextJson() != null
          && !msg.getRetrievedContextJson().isBlank()) {
        try {
          return objectMapper.readValue(
              msg.getRetrievedContextJson(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
          log.warn(
              "Failed to parse retrievedContextJson for message {}: {}",
              msg.getId(),
              e.getMessage());
        }
      }
    }
    return List.of();
  }

  private ChatMessageDocument toChatMessageDocument(ChatMessage msg) {
    return ChatMessageDocument.builder()
        .id(msg.getId().toString())
        .sessionId(msg.getSession().getId())
        .role(msg.getRole().name())
        .content(msg.getContent())
        .timestamp(
            msg.getCreatedAt() != null
                ? msg.getCreatedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                : System.currentTimeMillis())
        .tokenCount(msg.getTokenCount() != null ? msg.getTokenCount() : 0)
        .build();
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
