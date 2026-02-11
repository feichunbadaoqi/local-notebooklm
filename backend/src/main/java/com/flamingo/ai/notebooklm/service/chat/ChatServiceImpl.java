package com.flamingo.ai.notebooklm.service.chat;

import com.flamingo.ai.notebooklm.api.dto.response.StreamChunkResponse;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.entity.ChatSummary;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.domain.repository.ChatSummaryRepository;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.service.rag.HybridSearchService;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/** Implementation of ChatService with RAG-enhanced streaming responses. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

  private final SessionService sessionService;
  private final HybridSearchService hybridSearchService;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatSummaryRepository chatSummaryRepository;
  private final StreamingChatModel streamingChatModel;
  private final ChatCompactionService compactionService;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  @Override
  @CircuitBreaker(name = "openai", fallbackMethod = "streamChatFallback")
  @Retry(name = "openai")
  public Flux<StreamChunkResponse> streamChat(UUID sessionId, String userMessage) {
    Timer.Sample sample = Timer.start(meterRegistry);

    Session session = sessionService.getSession(sessionId);
    InteractionMode mode = session.getCurrentMode();

    // Save user message
    saveMessage(session, MessageRole.USER, userMessage, mode);

    // Retrieve relevant context via RAG
    List<DocumentChunk> relevantChunks = hybridSearchService.search(sessionId, userMessage, mode);
    String context = hybridSearchService.buildContext(relevantChunks);

    // Build conversation context
    List<dev.langchain4j.data.message.ChatMessage> messages =
        buildConversationContext(session, mode, context, userMessage);

    // Create sink for streaming
    Sinks.Many<StreamChunkResponse> sink = Sinks.many().unicast().onBackpressureBuffer();
    AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());
    AtomicInteger tokenCount = new AtomicInteger(0);

    // Stream the response using StreamingChatResponseHandler
    streamingChatModel.chat(
        messages,
        new StreamingChatResponseHandler() {
          @Override
          public void onPartialResponse(String partialResponse) {
            responseBuilder.get().append(partialResponse);
            tokenCount.incrementAndGet();
            sink.tryEmitNext(StreamChunkResponse.token(partialResponse));
          }

          @Override
          public void onCompleteResponse(ChatResponse completeResponse) {
            // Send citations if we have relevant chunks
            if (!relevantChunks.isEmpty()) {
              for (DocumentChunk chunk : relevantChunks) {
                sink.tryEmitNext(
                    StreamChunkResponse.citation(
                        chunk.getFileName(),
                        null,
                        chunk.getContent().substring(0, Math.min(100, chunk.getContent().length()))
                            + "..."));
              }
            }

            // Save assistant message
            String fullResponse = responseBuilder.get().toString();
            ChatMessage assistantMsg =
                saveMessage(session, MessageRole.ASSISTANT, fullResponse, mode);

            // Update metrics
            meterRegistry.counter("chat.messages.generated").increment();
            meterRegistry.counter("chat.tokens.generated").increment(tokenCount.get());

            // Check if compaction is needed
            compactionService.checkAndCompactIfNeeded(sessionId);

            // Send done event
            sink.tryEmitNext(
                StreamChunkResponse.done(assistantMsg.getId().toString(), 0, tokenCount.get()));
            sink.tryEmitComplete();

            sample.stop(meterRegistry.timer("chat.stream.duration"));
          }

          @Override
          public void onError(Throwable error) {
            log.error("Error during chat streaming: {}", error.getMessage());
            sink.tryEmitNext(
                StreamChunkResponse.error(UUID.randomUUID().toString(), error.getMessage()));
            sink.tryEmitComplete();
            meterRegistry.counter("chat.errors").increment();
          }
        });

    return sink.asFlux();
  }

  @SuppressWarnings("unused")
  private Flux<StreamChunkResponse> streamChatFallback(
      UUID sessionId, String userMessage, Throwable t) {
    log.error("Chat service circuit breaker open: {}", t.getMessage());
    return Flux.just(
        StreamChunkResponse.error(
            UUID.randomUUID().toString(),
            "The AI service is temporarily unavailable. Please try again in a moment."));
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChatMessage> getChatHistory(UUID sessionId, int limit) {
    sessionService.getSession(sessionId); // Validate session exists
    return chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId).stream()
        .limit(limit)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChatMessage> getRecentMessages(UUID sessionId) {
    int windowSize = ragConfig.getCompaction().getSlidingWindowSize();
    return chatMessageRepository.findRecentNonCompactedMessages(sessionId, windowSize);
  }

  private List<dev.langchain4j.data.message.ChatMessage> buildConversationContext(
      Session session, InteractionMode mode, String ragContext, String currentMessage) {

    List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

    // System prompt based on mode
    String systemPrompt = buildSystemPrompt(mode, ragContext);
    messages.add(SystemMessage.from(systemPrompt));

    // Add chat summary if available
    List<ChatSummary> summaries =
        chatSummaryRepository.findBySessionIdOrderByCreatedAtDesc(session.getId());
    if (!summaries.isEmpty()) {
      ChatSummary latestSummary = summaries.get(0);
      messages.add(
          SystemMessage.from(
              "Previous conversation summary: " + latestSummary.getSummaryContent()));
    }

    // Add recent messages
    List<ChatMessage> recentMessages = getRecentMessages(session.getId());
    for (ChatMessage msg : recentMessages) {
      if (msg.getRole() == MessageRole.USER) {
        messages.add(UserMessage.from(msg.getContent()));
      } else if (msg.getRole() == MessageRole.ASSISTANT) {
        messages.add(AiMessage.from(msg.getContent()));
      }
    }

    // Add current user message
    messages.add(UserMessage.from(currentMessage));

    return messages;
  }

  private String buildSystemPrompt(InteractionMode mode, String ragContext) {
    StringBuilder prompt = new StringBuilder();

    prompt.append("You are a helpful AI assistant for document Q&A. ");

    switch (mode) {
      case EXPLORING ->
          prompt.append(
              "In EXPLORING mode, encourage broad discovery. Suggest related topics and connections. "
                  + "Help the user discover new insights from their documents.");
      case RESEARCH ->
          prompt.append(
              "In RESEARCH mode, focus on precision and citations. Always cite specific sources. "
                  + "Provide fact-focused, accurate responses with clear references.");
      case LEARNING ->
          prompt.append(
              "In LEARNING mode, use the Socratic method. Ask clarifying questions. "
                  + "Build understanding progressively. Explain concepts step by step.");
      default ->
          prompt.append("Provide helpful, accurate responses based on the available information.");
    }

    if (!ragContext.isEmpty()) {
      prompt.append("\n\n").append(ragContext);
    }

    prompt.append(
        "\n\nProvide helpful, accurate responses based on the available information. "
            + "If you don't know something or it's not in the provided context, say so clearly.");

    return prompt.toString();
  }

  @Transactional
  protected ChatMessage saveMessage(
      Session session, MessageRole role, String content, InteractionMode mode) {
    ChatMessage message =
        ChatMessage.builder()
            .session(session)
            .role(role)
            .content(content)
            .modeUsed(mode)
            .tokenCount(estimateTokenCount(content))
            .isCompacted(false)
            .build();

    return chatMessageRepository.save(message);
  }

  private int estimateTokenCount(String text) {
    // Rough estimate: ~4 characters per token for English
    return text.length() / 4;
  }
}
