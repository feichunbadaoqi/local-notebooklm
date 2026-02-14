package com.flamingo.ai.notebooklm.service.chat;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.entity.ChatSummary;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.domain.repository.ChatSummaryRepository;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import dev.langchain4j.model.chat.ChatModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for compacting chat history by summarizing older messages.
 *
 * <p>Compaction strategy: - Keep last N messages in full (sliding window) - Summarize messages
 * beyond the window - Store summaries for context reconstruction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatCompactionService {

  private final ChatMessageRepository chatMessageRepository;
  private final ChatSummaryRepository chatSummaryRepository;
  private final SessionService sessionService;
  private final ChatModel chatModel;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  /**
   * Checks if compaction is needed and triggers it asynchronously.
   *
   * @param sessionId the session to check
   */
  public void checkAndCompactIfNeeded(UUID sessionId) {
    List<ChatMessage> nonCompactedMessages =
        chatMessageRepository.findNonCompactedMessagesBySessionId(sessionId);

    int messageThreshold = ragConfig.getCompaction().getMessageThreshold();
    int tokenThreshold = ragConfig.getCompaction().getTokenThreshold();

    int totalTokens =
        nonCompactedMessages.stream()
            .mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
            .sum();

    if (nonCompactedMessages.size() > messageThreshold || totalTokens > tokenThreshold) {
      log.info(
          "Compaction triggered for session {} (messages: {}, tokens: {})",
          sessionId,
          nonCompactedMessages.size(),
          totalTokens);
      compactAsync(sessionId);
    }
  }

  /**
   * Compacts chat history asynchronously.
   *
   * @param sessionId the session to compact
   */
  @Async("documentProcessingExecutor")
  public void compactAsync(UUID sessionId) {
    try {
      compact(sessionId);
    } catch (Exception e) {
      log.error("Failed to compact session {}: {}", sessionId, e.getMessage());
      meterRegistry.counter("chat.compaction.errors").increment();
    }
  }

  /**
   * Forces immediate compaction of a session's chat history.
   *
   * @param sessionId the session to compact
   */
  @Timed(value = "chat.compaction", description = "Time to compact chat history")
  @Transactional
  @CircuitBreaker(name = "openai", fallbackMethod = "compactFallback")
  public void compact(UUID sessionId) {
    Session session = sessionService.getSession(sessionId);
    List<ChatMessage> nonCompactedMessages =
        chatMessageRepository.findNonCompactedMessagesBySessionId(sessionId);

    if (nonCompactedMessages.isEmpty()) {
      return;
    }

    int slidingWindowSize = ragConfig.getCompaction().getSlidingWindowSize();
    int batchSize = ragConfig.getCompaction().getBatchSize();

    // Keep recent messages, compact older ones
    if (nonCompactedMessages.size() <= slidingWindowSize) {
      return;
    }

    // Messages to compact (older messages beyond the sliding window)
    List<ChatMessage> messagesToCompact =
        nonCompactedMessages.stream()
            .skip(slidingWindowSize)
            .limit(batchSize)
            .collect(Collectors.toList());

    if (messagesToCompact.isEmpty()) {
      return;
    }

    // Generate summary
    String summary = generateSummary(messagesToCompact);

    // Calculate original token count
    int originalTokens =
        messagesToCompact.stream()
            .mapToInt(m -> m.getTokenCount() != null ? m.getTokenCount() : 0)
            .sum();

    // Create summary record
    ChatSummary chatSummary =
        ChatSummary.builder()
            .session(session)
            .summaryContent(summary)
            .messageCount(messagesToCompact.size())
            .tokenCount(estimateTokenCount(summary))
            .originalTokenCount(originalTokens)
            .fromTimestamp(messagesToCompact.get(0).getCreatedAt())
            .toTimestamp(messagesToCompact.get(messagesToCompact.size() - 1).getCreatedAt())
            .build();

    chatSummaryRepository.save(chatSummary);

    // Mark messages as compacted
    for (ChatMessage message : messagesToCompact) {
      message.markCompacted(chatSummary);
    }
    chatMessageRepository.saveAll(messagesToCompact);

    meterRegistry.counter("chat.compaction.success").increment();
    meterRegistry.counter("chat.messages.compacted").increment(messagesToCompact.size());

    log.info("Compacted {} messages for session {}", messagesToCompact.size(), sessionId);
  }

  @SuppressWarnings("unused")
  private void compactFallback(UUID sessionId, Throwable t) {
    log.warn("Compaction fallback triggered for session {}: {}", sessionId, t.getMessage());
    meterRegistry.counter("chat.compaction.fallback").increment();
  }

  private String generateSummary(List<ChatMessage> messages) {
    StringBuilder conversation = new StringBuilder();
    conversation.append("Summarize the following conversation concisely, ");
    conversation.append("preserving key facts, decisions, and context:\n\n");

    for (ChatMessage message : messages) {
      conversation.append(message.getRole().name()).append(": ");
      conversation.append(message.getContent()).append("\n\n");
    }

    String prompt = conversation.toString();

    try {
      return chatModel.chat(prompt);
    } catch (Exception e) {
      log.error("Failed to generate summary: {}", e.getMessage());
      // Fallback: just concatenate first sentences
      return messages.stream()
          .map(m -> m.getRole().name() + ": " + truncate(m.getContent(), 100))
          .collect(Collectors.joining("\n"));
    }
  }

  private int estimateTokenCount(String text) {
    // Rough estimate: ~4 characters per token for English
    return text.length() / 4;
  }

  private String truncate(String text, int maxLength) {
    if (text.length() <= maxLength) {
      return text;
    }
    return text.substring(0, maxLength) + "...";
  }
}
