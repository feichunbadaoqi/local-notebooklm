package com.flamingo.ai.notebooklm.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flamingo.ai.notebooklm.agent.ChatStreamingAgent;
import com.flamingo.ai.notebooklm.api.dto.response.StreamChunkResponse;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.entity.ChatSummary;
import com.flamingo.ai.notebooklm.domain.entity.Memory;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.domain.repository.ChatSummaryRepository;
import com.flamingo.ai.notebooklm.elasticsearch.ChatMessageDocument;
import com.flamingo.ai.notebooklm.elasticsearch.ChatMessageIndexService;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.service.memory.MemoryService;
import com.flamingo.ai.notebooklm.service.rag.embedding.EmbeddingService;
import com.flamingo.ai.notebooklm.service.rag.query.QueryReformulationService;
import com.flamingo.ai.notebooklm.service.rag.query.ReformulatedQuery;
import com.flamingo.ai.notebooklm.service.rag.search.HybridSearchService;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
  private final ChatStreamingAgent chatStreamingAgent;
  private final ChatCompactionService compactionService;
  private final MemoryService memoryService;
  private final QueryReformulationService queryReformulationService;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;
  private final com.flamingo.ai.notebooklm.service.rag.search.RetrievalConfidenceService
      confidenceService;
  private final ChatMessageIndexService chatMessageIndexService;
  private final EmbeddingService embeddingService;
  private final ObjectMapper objectMapper;

  @Override
  @Timed(value = "chat.stream", description = "Time to stream chat response")
  @CircuitBreaker(name = "openai", fallbackMethod = "streamChatFallback")
  @Retry(name = "openai")
  public Flux<StreamChunkResponse> streamChat(UUID sessionId, String userMessage) {
    log.debug("streamChat called for session {} with message: {}", sessionId, userMessage);

    Session session = sessionService.getSession(sessionId);
    InteractionMode mode = session.getCurrentMode();
    log.debug("Session found with mode: {}", mode);

    // Save user message
    saveMessage(session, MessageRole.USER, userMessage, mode);
    log.debug("User message saved");

    // Reformulate query with conversation context
    ReformulatedQuery reformulated =
        queryReformulationService.reformulate(sessionId, userMessage, mode);
    String searchQuery = reformulated.query();
    log.debug(
        "Original: {} | Rewritten: {} | followUp={} | anchorDocs={}",
        userMessage,
        searchQuery,
        reformulated.isFollowUp(),
        reformulated.anchorDocumentIds().size());

    // Retrieve relevant context via RAG (with details for confidence calculation)
    log.debug("Starting hybrid search for context...");
    HybridSearchService.SearchResult searchResult;
    if (reformulated.isFollowUp()
        && !reformulated.anchorDocumentIds().isEmpty()
        && ragConfig.getRetrieval().isSourceAnchoringEnabled()) {
      searchResult =
          hybridSearchService.searchWithDetails(
              sessionId, searchQuery, mode, reformulated.anchorDocumentIds());
    } else {
      searchResult = hybridSearchService.searchWithDetails(sessionId, searchQuery, mode);
    }
    List<DocumentChunk> relevantChunks = searchResult.finalResults();
    log.debug("Hybrid search returned {} chunks", relevantChunks.size());

    // Calculate retrieval confidence
    com.flamingo.ai.notebooklm.service.rag.search.RetrievalConfidenceService.ConfidenceScore
        confidence =
            confidenceService.calculateConfidence(
                searchResult.vectorResults(),
                searchResult.bm25Results(),
                relevantChunks,
                searchQuery);
    log.info("Retrieval confidence: {} ({})", confidence.level(), confidence.explanation());

    // Check if confidence is too low to answer
    if (confidence.level()
        == com.flamingo.ai.notebooklm.service.rag.search.RetrievalConfidenceService.ConfidenceLevel
            .LOW) {
      log.warn("Low confidence retrieval, returning insufficient information message");
      return Flux.just(
          StreamChunkResponse.token(
              "I don't have enough information in the uploaded documents to answer this question confidently. "
                  + "Could you rephrase your question or upload more relevant documents?"),
          StreamChunkResponse.done(UUID.randomUUID().toString(), 0, 0));
    }

    String context = hybridSearchService.buildContext(relevantChunks);
    log.debug("Built context with length: {}", context.length());

    // Build conversation context (add uncertainty note for medium confidence)
    String systemPromptSuffix = "";
    if (confidence.level()
        == com.flamingo.ai.notebooklm.service.rag.search.RetrievalConfidenceService.ConfidenceLevel
            .MEDIUM) {
      systemPromptSuffix =
          "\n\nNote: Evidence quality is moderate. "
              + "Be explicit about uncertainty and cite specific passages.";
    }

    List<dev.langchain4j.data.message.ChatMessage> messages =
        buildConversationContext(session, mode, context, userMessage, systemPromptSuffix);
    log.debug("Built conversation context with {} messages", messages.size());

    // Create sink for streaming
    Sinks.Many<StreamChunkResponse> sink = Sinks.many().unicast().onBackpressureBuffer();
    AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());
    AtomicInteger tokenCount = new AtomicInteger(0);

    log.debug("Calling chatStreamingAgent.chat()...");
    // Stream the response using TokenStream
    chatStreamingAgent
        .chat(messages)
        .onPartialResponse(
            token -> {
              log.trace("Received token: {}", token);
              responseBuilder.get().append(token);
              tokenCount.incrementAndGet();
              var result = sink.tryEmitNext(StreamChunkResponse.token(token));
              if (result.isFailure()) {
                log.warn("Failed to emit token: {}", result);
              }
            })
        .onCompleteResponse(
            response -> {
              log.debug("onCompleteResponse called, total tokens: {}", tokenCount.get());
              // Send citations if we have relevant chunks
              if (!relevantChunks.isEmpty()) {
                log.debug("Sending {} citations", relevantChunks.size());
                for (DocumentChunk chunk : relevantChunks) {
                  // Skip chunks without documentId (shouldn't happen in production, but test-safe)
                  if (chunk.getDocumentId() == null) {
                    log.warn("Skipping citation for chunk with null documentId: {}", chunk.getId());
                    continue;
                  }
                  sink.tryEmitNext(
                      StreamChunkResponse.citation(
                          chunk.getDocumentId().toString(),
                          chunk.getFileName(),
                          null,
                          chunk
                                  .getContent()
                                  .substring(0, Math.min(100, chunk.getContent().length()))
                              + "...",
                          chunk.getAssociatedImageIds(),
                          chunk.getSectionBreadcrumb()));
                }
              }

              // Save assistant message with retrieved context for follow-up anchoring
              String fullResponse = responseBuilder.get().toString();
              log.debug("Full response length: {}", fullResponse.length());
              String retrievedContextJson = serializeDocumentIds(relevantChunks);
              ChatMessage assistantMsg =
                  saveMessage(
                      session, MessageRole.ASSISTANT, fullResponse, mode, retrievedContextJson);
              log.debug("Assistant message saved with id: {}", assistantMsg.getId());

              // Update metrics
              meterRegistry.counter("chat.messages.generated").increment();
              meterRegistry.counter("chat.tokens.generated").increment(tokenCount.get());

              // Extract memories asynchronously (non-blocking)
              memoryService.extractAndSaveAsync(sessionId, userMessage, fullResponse, mode);

              // Check if compaction is needed
              compactionService.checkAndCompactIfNeeded(sessionId);

              // Send done event
              sink.tryEmitNext(
                  StreamChunkResponse.done(assistantMsg.getId().toString(), 0, tokenCount.get()));
              sink.tryEmitComplete();
              log.debug("Chat stream completed successfully");
            })
        .onError(
            error -> {
              log.error("Error during chat streaming: {}", error.getMessage(), error);
              sink.tryEmitNext(
                  StreamChunkResponse.error(UUID.randomUUID().toString(), error.getMessage()));
              sink.tryEmitComplete();
              meterRegistry.counter("chat.errors").increment();
            })
        .start();

    log.debug("chatStreamingAgent.chat() started, returning flux");
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
    // Get latest N messages (DESC), then reverse to display oldest-first (ASC)
    List<ChatMessage> messages =
        chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId).stream()
            .limit(limit)
            .toList();
    // Reverse to get chronological order (oldest first, newest at bottom)
    return messages.reversed();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChatMessage> getRecentMessages(UUID sessionId) {
    int windowSize = ragConfig.getCompaction().getSlidingWindowSize();
    return chatMessageRepository.findRecentNonCompactedMessages(sessionId, windowSize);
  }

  private List<dev.langchain4j.data.message.ChatMessage> buildConversationContext(
      Session session,
      InteractionMode mode,
      String ragContext,
      String currentMessage,
      String systemPromptSuffix) {

    List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

    // System prompt based on mode
    String systemPrompt = buildSystemPrompt(mode, ragContext) + systemPromptSuffix;
    messages.add(SystemMessage.from(systemPrompt));

    // Add relevant memories
    int memoryLimit = ragConfig.getMemory().getContextLimit();
    List<Memory> memories =
        memoryService.getRelevantMemories(session.getId(), currentMessage, memoryLimit);
    if (!memories.isEmpty()) {
      String memoryContext = memoryService.buildMemoryContext(memories);
      messages.add(SystemMessage.from(memoryContext));
    }

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

    prompt.append(
        "You are a helpful AI assistant. You can answer questions about the user's uploaded "
            + "documents, but you can also have general conversations and answer questions on "
            + "any topic using your general knowledge.\n\n");

    switch (mode) {
      case EXPLORING ->
          prompt.append(
              "Current mode: EXPLORING - Encourage broad discovery. Suggest related topics and "
                  + "connections. Help the user discover new insights.");
      case RESEARCH ->
          prompt.append(
              "Current mode: RESEARCH - Focus on precision and citations. When referencing "
                  + "documents, cite specific sources. Provide fact-focused, accurate responses.");
      case LEARNING ->
          prompt.append(
              "Current mode: LEARNING - Use the Socratic method. Ask clarifying questions. "
                  + "Build understanding progressively. Explain concepts step by step.");
      default -> prompt.append("Provide helpful, accurate responses.");
    }

    // Instructions for handling image references
    prompt.append(
        "\n\nIMPORTANT - Image References:\n"
            + "The document context may include image markers in the format:\n"
            + "[IMAGE: filename - Figure N - ID: uuid]\n\n"
            + "When you encounter these markers:\n"
            + "1. Reference them naturally in your response (e.g., \"As shown in Figure 1...\")\n"
            + "2. Include the EXACT marker in your response where relevant\n"
            + "3. The UI will automatically render these as actual images\n"
            + "4. Do NOT describe the image content unless you have textual context\n"
            + "5. If asked about an image's visual content, say \"Please refer to the image above\"\n\n");

    if (!ragContext.isEmpty()) {
      prompt.append("\n\n").append(ragContext);
      prompt.append(
          "\n\nIMPORTANT: Only use the document context above if it is RELEVANT to the user's "
              + "question. If the user asks about something unrelated to the documents "
              + "(like weather, general knowledge, coding help, etc.), ignore the document "
              + "context and respond using your general knowledge.");
    } else {
      prompt.append("\n\nNo document context is available. Respond using your general knowledge.");
    }

    return prompt.toString();
  }

  @Transactional
  protected ChatMessage saveMessage(
      Session session, MessageRole role, String content, InteractionMode mode) {
    return saveMessage(session, role, content, mode, null);
  }

  @Transactional
  protected ChatMessage saveMessage(
      Session session,
      MessageRole role,
      String content,
      InteractionMode mode,
      String retrievedContextJson) {
    ChatMessage message =
        ChatMessage.builder()
            .session(session)
            .role(role)
            .content(content)
            .modeUsed(mode)
            .tokenCount(estimateTokenCount(content))
            .isCompacted(false)
            .retrievedContextJson(retrievedContextJson)
            .build();

    ChatMessage saved = chatMessageRepository.save(message);

    // Index to Elasticsearch asynchronously for semantic search
    try {
      List<Float> embedding = embeddingService.embedText(content);
      ChatMessageDocument doc =
          ChatMessageDocument.builder()
              .id(saved.getId().toString())
              .sessionId(session.getId())
              .role(role.name())
              .content(content)
              .embedding(embedding)
              .timestamp(System.currentTimeMillis())
              .tokenCount(saved.getTokenCount())
              .build();

      chatMessageIndexService.indexMessages(List.of(doc));
      log.debug("Indexed chat message {} to Elasticsearch", saved.getId());
    } catch (Exception e) {
      // Don't fail the request if ES indexing fails
      log.warn(
          "Failed to index chat message {} to Elasticsearch: {}", saved.getId(), e.getMessage());
    }

    return saved;
  }

  private String serializeDocumentIds(List<DocumentChunk> chunks) {
    if (chunks.isEmpty()) {
      return null;
    }
    List<String> docIds =
        chunks.stream()
            .filter(c -> c.getDocumentId() != null)
            .map(c -> c.getDocumentId().toString())
            .distinct()
            .collect(Collectors.toList());
    if (docIds.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(docIds);
    } catch (Exception e) {
      log.warn("Failed to serialize document IDs: {}", e.getMessage());
      return null;
    }
  }

  private int estimateTokenCount(String text) {
    // Rough estimate: ~4 characters per token for English
    return text.length() / 4;
  }
}
