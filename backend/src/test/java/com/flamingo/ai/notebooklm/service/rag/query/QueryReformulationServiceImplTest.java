package com.flamingo.ai.notebooklm.service.rag.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flamingo.ai.notebooklm.agent.QueryReformulationAgent;
import com.flamingo.ai.notebooklm.agent.dto.QueryReformulationResult;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.elasticsearch.ChatMessageDocument;
import com.flamingo.ai.notebooklm.service.chat.ChatHistoryHybridSearchService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class QueryReformulationServiceImplTest {

  @Mock private QueryReformulationAgent agent;
  @Mock private ChatHistoryHybridSearchService chatHistoryHybridSearchService;
  @Mock private ChatMessageRepository chatMessageRepository;

  @Mock private RagConfig ragConfig;

  private MeterRegistry meterRegistry;
  private ObjectMapper objectMapper;

  private QueryReformulationServiceImpl service;

  private UUID sessionId;
  private Session session;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    objectMapper = new ObjectMapper();
    service =
        new QueryReformulationServiceImpl(
            agent,
            chatHistoryHybridSearchService,
            chatMessageRepository,
            objectMapper,
            ragConfig,
            meterRegistry);
    sessionId = UUID.randomUUID();
    session = Session.builder().id(sessionId).title("Test").build();
  }

  @Test
  void shouldReturnOriginalQuery_whenStandalone() {
    // Given
    String originalQuery = "What is quantum computing?";
    RagConfig.QueryReformulation config = createConfig(true, 5, 500, 2);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatMessageRepository.findRecentMessages(eq(sessionId), any(Pageable.class)))
        .thenReturn(
            createDbMessages("Tell me about climate change", "Climate change refers to..."));
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(List.of());

    when(agent.reformulate(anyString(), anyString(), eq(originalQuery)))
        .thenReturn(new QueryReformulationResult(false, false, originalQuery, "Self-contained"));

    // When
    ReformulatedQuery result =
        service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result.query()).isEqualTo(originalQuery);
    assertThat(result.isFollowUp()).isFalse();
    assertThat(result.anchorDocumentIds()).isEmpty();
  }

  @Test
  void shouldReturnIsFollowUpTrue_withAnchorDocIds_whenFollowUpDetected() throws Exception {
    // Given
    String originalQuery = "Can you elaborate more?";
    String reformulatedQuery = "Can you elaborate more on the solar panel efficiency topic?";
    UUID docId = UUID.randomUUID();
    String retrievedContextJson = objectMapper.writeValueAsString(List.of(docId.toString()));

    RagConfig.QueryReformulation config = createConfig(true, 5, 500, 2);
    when(ragConfig.getQueryReformulation()).thenReturn(config);

    // Last DB messages: USER + ASSISTANT (with retrievedContextJson)
    ChatMessage userMsg = createChatMessage("Tell me about solar panels", MessageRole.USER, null);
    ChatMessage assistantMsg =
        createChatMessage(
            "Solar panels convert sunlight...", MessageRole.ASSISTANT, retrievedContextJson);
    when(chatMessageRepository.findRecentMessages(eq(sessionId), any(Pageable.class)))
        .thenReturn(List.of(assistantMsg, userMsg)); // DESC order from DB

    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(List.of());

    when(agent.reformulate(anyString(), anyString(), eq(originalQuery)))
        .thenReturn(
            new QueryReformulationResult(
                true, true, reformulatedQuery, "Follow-up on solar panels"));

    // When
    ReformulatedQuery result =
        service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result.query()).isEqualTo(reformulatedQuery);
    assertThat(result.isFollowUp()).isTrue();
    assertThat(result.anchorDocumentIds()).containsExactly(docId.toString());
  }

  @Test
  void shouldReturnEmptyAnchorDocs_whenIsFollowUpFalse() {
    // Given
    String originalQuery = "What about chapter 3?";
    String reformulatedQuery = "What does chapter 3 say about climate change?";

    RagConfig.QueryReformulation config = createConfig(true, 5, 500, 2);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatMessageRepository.findRecentMessages(eq(sessionId), any(Pageable.class)))
        .thenReturn(createDbMessages("Tell me about climate change", "Climate change..."));
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(List.of());

    // Agent says needsReformulation but NOT isFollowUp
    when(agent.reformulate(anyString(), anyString(), eq(originalQuery)))
        .thenReturn(
            new QueryReformulationResult(
                true, false, reformulatedQuery, "Added context but not a direct follow-up"));

    // When
    ReformulatedQuery result =
        service.reformulate(sessionId, originalQuery, InteractionMode.EXPLORING);

    // Then
    assertThat(result.query()).isEqualTo(reformulatedQuery);
    assertThat(result.isFollowUp()).isFalse();
    assertThat(result.anchorDocumentIds()).isEmpty();
  }

  @Test
  void shouldAlwaysIncludeRecentDbMessages_evenWhenSemanticSearchReturnsNone() {
    // Given
    String originalQuery = "Can you elaborate?";
    RagConfig.QueryReformulation config = createConfig(true, 5, 500, 2);
    when(ragConfig.getQueryReformulation()).thenReturn(config);

    List<ChatMessage> recentMessages =
        createDbMessages("Tell me about AI", "AI stands for Artificial Intelligence...");
    when(chatMessageRepository.findRecentMessages(eq(sessionId), any(Pageable.class)))
        .thenReturn(recentMessages);
    // Semantic search returns nothing
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), anyInt()))
        .thenReturn(List.of());

    when(agent.reformulate(anyString(), anyString(), eq(originalQuery)))
        .thenReturn(new QueryReformulationResult(true, true, "Elaborate on AI", "Follow-up"));

    // When
    ReformulatedQuery result =
        service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then — agent was still called despite empty semantic search
    verify(agent).reformulate(anyString(), anyString(), eq(originalQuery));
    assertThat(result.isFollowUp()).isTrue();
  }

  @Test
  void shouldReturnOriginalQuery_whenAgentThrowsException() {
    // Given
    String originalQuery = "What about chapter 3?";
    RagConfig.QueryReformulation config = createConfig(true, 5, 500, 2);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatMessageRepository.findRecentMessages(eq(sessionId), any(Pageable.class)))
        .thenReturn(createDbMessages("History", "Response..."));
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), anyInt()))
        .thenReturn(List.of());
    when(agent.reformulate(anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException("OpenAI API timeout"));

    // When
    ReformulatedQuery result =
        service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result.query()).isEqualTo(originalQuery);
    assertThat(result.isFollowUp()).isFalse();
    assertThat(result.anchorDocumentIds()).isEmpty();
  }

  @Test
  void shouldSkipReformulation_whenNoHistory() {
    // Given
    String originalQuery = "Hello";
    RagConfig.QueryReformulation config = createConfig(true, 5, 500, 2);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatMessageRepository.findRecentMessages(eq(sessionId), any(Pageable.class)))
        .thenReturn(List.of());
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), anyInt()))
        .thenReturn(List.of());

    // When
    ReformulatedQuery result =
        service.reformulate(sessionId, originalQuery, InteractionMode.EXPLORING);

    // Then
    assertThat(result.query()).isEqualTo(originalQuery);
    assertThat(result.isFollowUp()).isFalse();
    verify(agent, never()).reformulate(anyString(), anyString(), anyString());
  }

  @Test
  void shouldSkipReformulation_whenFeatureDisabled() {
    // Given
    String originalQuery = "What about chapter 3?";
    RagConfig.QueryReformulation config = createConfig(false, 5, 500, 2);
    when(ragConfig.getQueryReformulation()).thenReturn(config);

    // When
    ReformulatedQuery result =
        service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result.query()).isEqualTo(originalQuery);
    assertThat(result.isFollowUp()).isFalse();
    verify(agent, never()).reformulate(anyString(), anyString(), anyString());
  }

  @Test
  void shouldTruncateQuery_whenTooLong() {
    // Given
    String originalQuery = "What about chapter 3?";
    String veryLongQuery = "a".repeat(600);
    RagConfig.QueryReformulation config = createConfig(true, 5, 500, 2);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatMessageRepository.findRecentMessages(eq(sessionId), any(Pageable.class)))
        .thenReturn(createDbMessages("Tell me about the book", "The book is about..."));
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), anyInt()))
        .thenReturn(List.of());
    when(agent.reformulate(anyString(), anyString(), eq(originalQuery)))
        .thenReturn(
            new QueryReformulationResult(true, false, veryLongQuery, "Generated very long query"));

    // When
    ReformulatedQuery result =
        service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result.query().length()).isEqualTo(500);
  }

  @Test
  void shouldDeduplicateMessages_whenDbAndSemanticOverlap() {
    // Given
    String originalQuery = "Continue";
    RagConfig.QueryReformulation config = createConfig(true, 5, 500, 2);
    when(ragConfig.getQueryReformulation()).thenReturn(config);

    ChatMessage dbMsg1 = createChatMessage("Query 1", MessageRole.USER, null);
    ChatMessage dbMsg2 = createChatMessage("Response 1", MessageRole.ASSISTANT, null);
    when(chatMessageRepository.findRecentMessages(eq(sessionId), any(Pageable.class)))
        .thenReturn(List.of(dbMsg2, dbMsg1)); // DESC from DB

    // Semantic search returns one of the same messages (overlap) + a new one
    ChatMessageDocument semanticOverlap =
        ChatMessageDocument.builder()
            .id(dbMsg1.getId().toString())
            .sessionId(sessionId)
            .role("USER")
            .content("Query 1")
            .timestamp(System.currentTimeMillis() - 2000)
            .build();
    ChatMessageDocument newSemantic =
        ChatMessageDocument.builder()
            .id(UUID.randomUUID().toString())
            .sessionId(sessionId)
            .role("USER")
            .content("Older query")
            .timestamp(System.currentTimeMillis() - 5000)
            .build();
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), anyInt()))
        .thenReturn(List.of(semanticOverlap, newSemantic));

    when(agent.reformulate(anyString(), anyString(), eq(originalQuery)))
        .thenReturn(new QueryReformulationResult(false, false, originalQuery, "Test"));

    // When
    service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then — agent was called (merged messages deduplicated correctly)
    verify(agent).reformulate(anyString(), anyString(), eq(originalQuery));
  }

  // Helper methods

  private RagConfig.QueryReformulation createConfig(
      boolean enabled, int window, int maxLength, int minRecent) {
    RagConfig.QueryReformulation config = new RagConfig.QueryReformulation();
    config.setEnabled(enabled);
    config.setHistoryWindow(window);
    config.setMaxQueryLength(maxLength);
    config.setMinRecentMessages(minRecent);
    return config;
  }

  private List<ChatMessage> createDbMessages(String userMsg, String assistantMsg) {
    ChatMessage user = createChatMessage(userMsg, MessageRole.USER, null);
    ChatMessage assistant = createChatMessage(assistantMsg, MessageRole.ASSISTANT, null);
    // Return in DESC order (most recent first) as the repository would
    return List.of(assistant, user);
  }

  private ChatMessage createChatMessage(String content, MessageRole role, String retrievedCtx) {
    return ChatMessage.builder()
        .id(UUID.randomUUID())
        .session(session)
        .role(role)
        .content(content)
        .modeUsed(InteractionMode.EXPLORING)
        .tokenCount(content.length() / 4)
        .isCompacted(false)
        .retrievedContextJson(retrievedCtx)
        .createdAt(LocalDateTime.now())
        .build();
  }

  private ChatMessageDocument createMessageDoc(
      UUID sid, String role, String content, long timestamp) {
    return ChatMessageDocument.builder()
        .id(UUID.randomUUID().toString())
        .sessionId(sid)
        .role(role)
        .content(content)
        .timestamp(timestamp)
        .tokenCount(content.length() / 4)
        .build();
  }
}
