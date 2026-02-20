package com.flamingo.ai.notebooklm.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flamingo.ai.notebooklm.agent.ChatStreamingAgent;
import com.flamingo.ai.notebooklm.api.dto.response.StreamChunkResponse;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.entity.Memory;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.domain.repository.ChatSummaryRepository;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.service.memory.MemoryService;
import com.flamingo.ai.notebooklm.service.rag.HybridSearchService;
import com.flamingo.ai.notebooklm.service.rag.QueryReformulationService;
import com.flamingo.ai.notebooklm.service.rag.ReformulatedQuery;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import dev.langchain4j.service.TokenStream;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

  @Mock private SessionService sessionService;
  @Mock private HybridSearchService hybridSearchService;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private ChatSummaryRepository chatSummaryRepository;
  @Mock private ChatStreamingAgent chatStreamingAgent;
  @Mock private ChatCompactionService compactionService;
  @Mock private MemoryService memoryService;
  @Mock private QueryReformulationService queryReformulationService;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;
  @Mock private Timer timer;
  @Mock private Timer.Sample timerSample;

  @Mock private com.flamingo.ai.notebooklm.service.rag.RetrievalConfidenceService confidenceService;

  @Mock
  private com.flamingo.ai.notebooklm.elasticsearch.ChatMessageIndexService chatMessageIndexService;

  @Mock private com.flamingo.ai.notebooklm.service.rag.EmbeddingService embeddingService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private RagConfig ragConfig;
  private ChatServiceImpl chatService;

  private UUID sessionId;
  private Session session;

  @BeforeEach
  void setUp() {
    ragConfig = new RagConfig();
    ragConfig.setCompaction(new RagConfig.Compaction());
    ragConfig.setMemory(new RagConfig.Memory());

    chatService =
        new ChatServiceImpl(
            sessionService,
            hybridSearchService,
            chatMessageRepository,
            chatSummaryRepository,
            chatStreamingAgent,
            compactionService,
            memoryService,
            queryReformulationService,
            ragConfig,
            meterRegistry,
            confidenceService,
            chatMessageIndexService,
            embeddingService,
            objectMapper);

    sessionId = UUID.randomUUID();
    session =
        Session.builder()
            .id(sessionId)
            .title("Test Session")
            .currentMode(InteractionMode.EXPLORING)
            .build();

    // Common mock setup - lenient for metrics that aren't used in all tests
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
    lenient().when(meterRegistry.timer(anyString())).thenReturn(timer);
  }

  @Nested
  @DisplayName("streamChat")
  class StreamChatTests {

    private void setupCommonMocks() {
      when(sessionService.getSession(sessionId)).thenReturn(session);
      lenient()
          .when(
              queryReformulationService.reformulate(
                  eq(sessionId), anyString(), any(InteractionMode.class)))
          .thenAnswer(
              invocation -> new ReformulatedQuery(invocation.getArgument(1), false, List.of()));

      HybridSearchService.SearchResult emptyResult =
          new HybridSearchService.SearchResult(List.of(), List.of(), List.of());
      // lenient: some tests override this stub
      lenient()
          .when(
              hybridSearchService.searchWithDetails(
                  eq(sessionId), anyString(), any(InteractionMode.class)))
          .thenReturn(emptyResult);
      lenient().when(hybridSearchService.buildContext(any())).thenReturn("");

      com.flamingo.ai.notebooklm.service.rag.RetrievalConfidenceService.ConfidenceScore
          highConfidence =
              new com.flamingo.ai.notebooklm.service.rag.RetrievalConfidenceService.ConfidenceScore(
                  0.8,
                  com.flamingo.ai.notebooklm.service.rag.RetrievalConfidenceService.ConfidenceLevel
                      .HIGH,
                  "High confidence");
      when(confidenceService.calculateConfidence(any(), any(), any(), anyString()))
          .thenReturn(highConfidence);
      when(chatSummaryRepository.findBySessionIdOrderByCreatedAtDesc(sessionId))
          .thenReturn(List.of());
      when(chatMessageRepository.findRecentNonCompactedMessages(eq(sessionId), anyInt()))
          .thenReturn(List.of());
      when(chatMessageRepository.save(any(ChatMessage.class)))
          .thenAnswer(
              inv -> {
                ChatMessage msg = inv.getArgument(0);
                msg.setId(UUID.randomUUID());
                return msg;
              });
      // lenient: shouldIncludeMemoriesInContext overrides this stub
      lenient()
          .when(memoryService.getRelevantMemories(eq(sessionId), anyString(), anyInt()))
          .thenReturn(List.of());
    }

    private void mockTokenStream(String... tokens) {
      TokenStream mockTokenStream = Mockito.mock(TokenStream.class);
      when(chatStreamingAgent.chat(any(List.class))).thenReturn(mockTokenStream);
      final java.util.function.Consumer<String>[] partialCallback =
          new java.util.function.Consumer[1];
      final java.util.function.Consumer[] completeCallback = new java.util.function.Consumer[1];
      final java.util.function.Consumer<Throwable>[] errorCallback =
          new java.util.function.Consumer[1];
      when(mockTokenStream.onPartialResponse(any()))
          .thenAnswer(
              inv -> {
                partialCallback[0] = inv.getArgument(0);
                return mockTokenStream;
              });
      when(mockTokenStream.onCompleteResponse(any()))
          .thenAnswer(
              inv -> {
                completeCallback[0] = inv.getArgument(0);
                return mockTokenStream;
              });
      when(mockTokenStream.onError(any()))
          .thenAnswer(
              inv -> {
                errorCallback[0] = inv.getArgument(0);
                return mockTokenStream;
              });
      doAnswer(
              inv -> {
                if (partialCallback[0] != null) {
                  for (String token : tokens) {
                    partialCallback[0].accept(token);
                  }
                }
                if (completeCallback[0] != null) {
                  completeCallback[0].accept(null);
                }
                return null;
              })
          .when(mockTokenStream)
          .start();
    }

    private void mockTokenStreamWithError(Throwable error) {
      TokenStream mockTokenStream = Mockito.mock(TokenStream.class);
      when(chatStreamingAgent.chat(any(List.class))).thenReturn(mockTokenStream);

      final java.util.function.Consumer<Throwable>[] errorCallback =
          new java.util.function.Consumer[1];

      when(mockTokenStream.onPartialResponse(any())).thenReturn(mockTokenStream);
      when(mockTokenStream.onCompleteResponse(any())).thenReturn(mockTokenStream);
      when(mockTokenStream.onError(any()))
          .thenAnswer(
              inv -> {
                errorCallback[0] = inv.getArgument(0);
                return mockTokenStream;
              });

      doAnswer(
              inv -> {
                if (errorCallback[0] != null) {
                  errorCallback[0].accept(error);
                }
                return null;
              })
          .when(mockTokenStream)
          .start();
    }

    @Test
    @DisplayName("should stream response tokens")
    void shouldStreamResponseTokens() {
      try (MockedStatic<Timer> timerMock =
          Mockito.mockStatic(Timer.class, Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        setupCommonMocks();
        mockTokenStream("Hello", " world");

        Flux<StreamChunkResponse> result = chatService.streamChat(sessionId, "Hi");

        StepVerifier.create(result)
            .assertNext(chunk -> assertThat(chunk.getEventType()).isEqualTo("token"))
            .assertNext(chunk -> assertThat(chunk.getEventType()).isEqualTo("token"))
            .assertNext(chunk -> assertThat(chunk.getEventType()).isEqualTo("done"))
            .verifyComplete();
      }
    }

    @Test
    @DisplayName("should include citations when documents are found")
    void shouldIncludeCitationsWhenDocumentsFound() {
      try (MockedStatic<Timer> timerMock =
          Mockito.mockStatic(Timer.class, Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        DocumentChunk chunk =
            DocumentChunk.builder()
                .id("chunk1")
                .fileName("test.pdf")
                .content(
                    "This is test content from the document that contains relevant information.")
                .build();

        setupCommonMocks();
        HybridSearchService.SearchResult searchResult =
            new HybridSearchService.SearchResult(List.of(chunk), List.of(chunk), List.of(chunk));
        when(hybridSearchService.searchWithDetails(
                eq(sessionId), anyString(), any(InteractionMode.class)))
            .thenReturn(searchResult);
        when(hybridSearchService.buildContext(any())).thenReturn("Context from documents");

        mockTokenStream("Response");

        Flux<StreamChunkResponse> result = chatService.streamChat(sessionId, "Query");

        StepVerifier.create(result)
            .assertNext(chunk1 -> assertThat(chunk1.getEventType()).isEqualTo("token"))
            .assertNext(chunk1 -> assertThat(chunk1.getEventType()).isEqualTo("citation"))
            .assertNext(chunk1 -> assertThat(chunk1.getEventType()).isEqualTo("done"))
            .verifyComplete();
      }
    }

    @Test
    @DisplayName("should save user and assistant messages")
    void shouldSaveUserAndAssistantMessages() {
      try (MockedStatic<Timer> timerMock =
          Mockito.mockStatic(Timer.class, Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        setupCommonMocks();

        mockTokenStream("Response");

        Flux<StreamChunkResponse> result = chatService.streamChat(sessionId, "Hello");
        result.collectList().block();

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, Mockito.times(2)).save(captor.capture());

        List<ChatMessage> savedMessages = captor.getAllValues();
        assertThat(savedMessages).hasSize(2);
        assertThat(savedMessages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(savedMessages.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
      }
    }

    @Test
    @DisplayName("should save retrievedContextJson on assistant message")
    void shouldSaveRetrievedContextJson_whenChunksFound() {
      try (MockedStatic<Timer> timerMock =
          Mockito.mockStatic(Timer.class, Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        UUID docId = UUID.randomUUID();
        DocumentChunk chunk =
            DocumentChunk.builder()
                .id("chunk1")
                .fileName("test.pdf")
                .documentId(docId)
                .content("Test content that is long enough for the test to work.")
                .build();

        setupCommonMocks();
        when(queryReformulationService.reformulate(
                eq(sessionId), anyString(), any(InteractionMode.class)))
            .thenReturn(new ReformulatedQuery("Test query", false, List.of()));
        HybridSearchService.SearchResult searchResult =
            new HybridSearchService.SearchResult(List.of(chunk), List.of(chunk), List.of(chunk));
        when(hybridSearchService.searchWithDetails(
                eq(sessionId), anyString(), any(InteractionMode.class)))
            .thenReturn(searchResult);
        when(hybridSearchService.buildContext(any())).thenReturn("Context");

        mockTokenStream("Response");

        chatService.streamChat(sessionId, "Query").collectList().block();

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, Mockito.times(2)).save(captor.capture());

        ChatMessage assistantMsg = captor.getAllValues().get(1);
        assertThat(assistantMsg.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(assistantMsg.getRetrievedContextJson()).contains(docId.toString());
      }
    }

    @Test
    @DisplayName("should call anchored search when follow-up with anchor doc IDs")
    void shouldCallAnchoredSearch_whenFollowUpDetected() {
      try (MockedStatic<Timer> timerMock =
          Mockito.mockStatic(Timer.class, Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        UUID anchorDocId = UUID.randomUUID();
        List<String> anchorIds = List.of(anchorDocId.toString());

        setupCommonMocks();
        when(queryReformulationService.reformulate(
                eq(sessionId), anyString(), any(InteractionMode.class)))
            .thenReturn(new ReformulatedQuery("Expanded follow-up", true, anchorIds));
        RagConfig.Retrieval retrieval = new RagConfig.Retrieval();
        retrieval.setSourceAnchoringEnabled(true);
        ragConfig.setRetrieval(retrieval);
        HybridSearchService.SearchResult searchResult =
            new HybridSearchService.SearchResult(List.of(), List.of(), List.of());
        when(hybridSearchService.searchWithDetails(
                eq(sessionId), anyString(), any(InteractionMode.class), eq(anchorIds)))
            .thenReturn(searchResult);

        mockTokenStream("Response");

        chatService.streamChat(sessionId, "Can you elaborate?").collectList().block();

        verify(hybridSearchService)
            .searchWithDetails(
                eq(sessionId), anyString(), any(InteractionMode.class), eq(anchorIds));
      }
    }

    @Test
    @DisplayName("should include memories in context")
    void shouldIncludeMemoriesInContext() {
      try (MockedStatic<Timer> timerMock =
          Mockito.mockStatic(Timer.class, Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        Memory memory =
            Memory.builder()
                .memoryContent("Important fact")
                .memoryType(Memory.TYPE_FACT)
                .importance(0.9f)
                .build();

        setupCommonMocks();
        when(memoryService.getRelevantMemories(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of(memory));
        when(memoryService.buildMemoryContext(any())).thenReturn("Memory context");

        mockTokenStream("Response");

        Flux<StreamChunkResponse> result = chatService.streamChat(sessionId, "Query");
        result.collectList().block();

        verify(memoryService).getRelevantMemories(eq(sessionId), anyString(), anyInt());
        verify(memoryService).buildMemoryContext(List.of(memory));
      }
    }

    @Test
    @DisplayName("should call memory extraction after response")
    void shouldCallMemoryExtractionAfterResponse() {
      try (MockedStatic<Timer> timerMock =
          Mockito.mockStatic(Timer.class, Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        setupCommonMocks();

        mockTokenStream("Test response");

        Flux<StreamChunkResponse> result = chatService.streamChat(sessionId, "Test query");
        result.collectList().block();

        verify(memoryService)
            .extractAndSaveAsync(
                eq(sessionId),
                eq("Test query"),
                eq("Test response"),
                eq(InteractionMode.EXPLORING));
      }
    }

    @Test
    @DisplayName("should handle streaming error gracefully")
    void shouldHandleStreamingErrorGracefully() {
      try (MockedStatic<Timer> timerMock =
          Mockito.mockStatic(Timer.class, Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        setupCommonMocks();

        mockTokenStreamWithError(new RuntimeException("LLM error"));

        Flux<StreamChunkResponse> result = chatService.streamChat(sessionId, "Query");

        StepVerifier.create(result)
            .assertNext(chunk -> assertThat(chunk.getEventType()).isEqualTo("error"))
            .verifyComplete();
      }
    }
  }

  @Test
  @DisplayName("should return limited chat history")
  void shouldReturnLimitedChatHistory() {
    ChatMessage msg1 = createChatMessage("User message", MessageRole.USER);
    ChatMessage msg2 = createChatMessage("Assistant response", MessageRole.ASSISTANT);
    ChatMessage msg3 = createChatMessage("Another message", MessageRole.USER);

    when(sessionService.getSession(sessionId)).thenReturn(session);
    when(chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId))
        .thenReturn(List.of(msg3, msg2, msg1));

    List<ChatMessage> result = chatService.getChatHistory(sessionId, 2);

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("should return recent non-compacted messages")
  void shouldReturnRecentNonCompactedMessages() {
    ChatMessage msg1 = createChatMessage("Message 1", MessageRole.USER);
    ChatMessage msg2 = createChatMessage("Message 2", MessageRole.ASSISTANT);

    when(chatMessageRepository.findRecentNonCompactedMessages(sessionId, 10))
        .thenReturn(List.of(msg1, msg2));

    List<ChatMessage> result = chatService.getRecentMessages(sessionId);

    assertThat(result).hasSize(2);
  }

  private ChatMessage createChatMessage(String content, MessageRole role) {
    return ChatMessage.builder()
        .id(UUID.randomUUID())
        .session(session)
        .content(content)
        .role(role)
        .modeUsed(InteractionMode.EXPLORING)
        .tokenCount(content.length() / 4)
        .isCompacted(false)
        .createdAt(LocalDateTime.now())
        .build();
  }
}
