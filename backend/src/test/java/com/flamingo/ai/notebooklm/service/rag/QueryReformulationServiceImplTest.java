package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.agent.QueryReformulationAgent;
import com.flamingo.ai.notebooklm.agent.dto.QueryReformulationResult;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import com.flamingo.ai.notebooklm.elasticsearch.ChatMessageDocument;
import com.flamingo.ai.notebooklm.service.chat.ChatHistoryHybridSearchService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryReformulationServiceImplTest {

  @Mock private QueryReformulationAgent agent;

  @Mock private ChatHistoryHybridSearchService chatHistoryHybridSearchService;

  @Mock private RagConfig ragConfig;

  private MeterRegistry meterRegistry;

  private QueryReformulationServiceImpl service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service =
        new QueryReformulationServiceImpl(
            agent, chatHistoryHybridSearchService, ragConfig, meterRegistry);
  }

  @Test
  void shouldReturnOriginalQuery_whenStandalone() {
    // Given
    UUID sessionId = UUID.randomUUID();
    String originalQuery = "What is quantum computing?";

    RagConfig.QueryReformulation config = createConfig(true, 5, 500);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(createHistory("Tell me about climate change", "Climate change refers to..."));

    // Mock agent to return standalone result
    when(agent.reformulate(anyString(), eq(originalQuery)))
        .thenReturn(new QueryReformulationResult(false, originalQuery, "Query is self-contained"));

    // When
    String result = service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result).isEqualTo(originalQuery);
    verify(agent).reformulate(anyString(), eq(originalQuery));
  }

  @Test
  void shouldReformulateQuery_whenPronounReference() {
    // Given
    UUID sessionId = UUID.randomUUID();
    String originalQuery = "How efficient are they?";
    String reformulatedQuery = "How efficient are solar panels?";

    RagConfig.QueryReformulation config = createConfig(true, 5, 500);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(
            createHistory("Tell me about solar panels", "Solar panels convert sunlight..."));

    // Mock agent to return reformulated result
    when(agent.reformulate(anyString(), eq(originalQuery)))
        .thenReturn(
            new QueryReformulationResult(
                true, reformulatedQuery, "Resolved pronoun 'they' to 'solar panels' from context"));

    // When
    String result = service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result).isEqualTo(reformulatedQuery);
    verify(agent).reformulate(anyString(), eq(originalQuery));
  }

  @Test
  void shouldReformulateQuery_whenFollowUp() {
    // Given
    UUID sessionId = UUID.randomUUID();
    String originalQuery = "What about chapter 3?";
    String reformulatedQuery = "What does chapter 3 say about climate change?";

    RagConfig.QueryReformulation config = createConfig(true, 5, 500);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(
            createHistory(
                "Tell me about the climate change report",
                "The report discusses various aspects of climate change..."));

    when(agent.reformulate(anyString(), eq(originalQuery)))
        .thenReturn(
            new QueryReformulationResult(
                true,
                reformulatedQuery,
                "Added context from previous discussion about climate change"));

    // When
    String result = service.reformulate(sessionId, originalQuery, InteractionMode.EXPLORING);

    // Then
    assertThat(result).isEqualTo(reformulatedQuery);
    assertThat(result).contains("climate change");
  }

  @Test
  void shouldReturnOriginalQuery_whenAgentThrowsException() {
    // Given
    UUID sessionId = UUID.randomUUID();
    String originalQuery = "What about chapter 3?";

    RagConfig.QueryReformulation config = createConfig(true, 5, 500);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(createHistory("Tell me about climate change", "Climate change refers to..."));

    // Mock agent to throw exception
    when(agent.reformulate(anyString(), anyString()))
        .thenThrow(new RuntimeException("OpenAI API timeout"));

    // When
    String result = service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result).isEqualTo(originalQuery);
  }

  @Test
  void shouldSkipReformulation_whenNoHistory() {
    // Given
    UUID sessionId = UUID.randomUUID();
    String originalQuery = "Hello";

    RagConfig.QueryReformulation config = createConfig(true, 5, 500);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(List.of());

    // When
    String result = service.reformulate(sessionId, originalQuery, InteractionMode.EXPLORING);

    // Then
    assertThat(result).isEqualTo(originalQuery);
    verify(agent, never()).reformulate(anyString(), anyString());
  }

  @Test
  void shouldSkipReformulation_whenFeatureDisabled() {
    // Given
    UUID sessionId = UUID.randomUUID();
    String originalQuery = "What about chapter 3?";

    RagConfig.QueryReformulation config = createConfig(false, 5, 500);
    when(ragConfig.getQueryReformulation()).thenReturn(config);

    // When
    String result = service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result).isEqualTo(originalQuery);
    verify(agent, never()).reformulate(anyString(), anyString());
  }

  @Test
  void shouldTruncateQuery_whenTooLong() {
    // Given
    UUID sessionId = UUID.randomUUID();
    String originalQuery = "What about chapter 3?";
    String veryLongQuery = "a".repeat(600);

    RagConfig.QueryReformulation config = createConfig(true, 5, 500);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(createHistory("Tell me about the book", "The book is about..."));

    when(agent.reformulate(anyString(), eq(originalQuery)))
        .thenReturn(new QueryReformulationResult(true, veryLongQuery, "Generated very long query"));

    // When
    String result = service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result.length()).isEqualTo(500);
  }

  @Test
  void shouldReturnOriginalQuery_whenReformulatedIsEmpty() {
    // Given
    UUID sessionId = UUID.randomUUID();
    String originalQuery = "What about chapter 3?";

    RagConfig.QueryReformulation config = createConfig(true, 5, 500);
    when(ragConfig.getQueryReformulation()).thenReturn(config);
    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(createHistory("Tell me about the book", "The book is about..."));

    when(agent.reformulate(anyString(), eq(originalQuery)))
        .thenReturn(new QueryReformulationResult(true, "", "Failed to reformulate"));

    // When
    String result = service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    assertThat(result).isEqualTo(originalQuery);
  }

  @Test
  void shouldBuildConversationHistory_inChronologicalOrder() {
    // Given
    UUID sessionId = UUID.randomUUID();
    String originalQuery = "Continue the discussion";

    RagConfig.QueryReformulation config = createConfig(true, 5, 500);
    when(ragConfig.getQueryReformulation()).thenReturn(config);

    // Hybrid search returns messages already in semantic relevance order
    // Service will sort by timestamp to get chronological order
    List<ChatMessageDocument> messages = new ArrayList<>();
    long now = System.currentTimeMillis();
    messages.add(createMessageDoc(sessionId, MessageRole.USER.name(), "Query 1", now - 3000));
    messages.add(
        createMessageDoc(sessionId, MessageRole.ASSISTANT.name(), "Response 1", now - 2000));
    messages.add(createMessageDoc(sessionId, MessageRole.USER.name(), "Query 2", now - 1000));
    messages.add(createMessageDoc(sessionId, MessageRole.ASSISTANT.name(), "Response 2", now));

    when(chatHistoryHybridSearchService.search(eq(sessionId), anyString(), eq(5)))
        .thenReturn(messages);

    when(agent.reformulate(anyString(), eq(originalQuery)))
        .thenReturn(new QueryReformulationResult(false, originalQuery, "Test"));

    // When
    service.reformulate(sessionId, originalQuery, InteractionMode.RESEARCH);

    // Then
    verify(agent)
        .reformulate(org.mockito.ArgumentMatchers.contains("User: Query 1"), eq(originalQuery));
  }

  // Helper methods

  private RagConfig.QueryReformulation createConfig(boolean enabled, int window, int maxLength) {
    RagConfig.QueryReformulation config = new RagConfig.QueryReformulation();
    config.setEnabled(enabled);
    config.setHistoryWindow(window);
    config.setMaxQueryLength(maxLength);
    return config;
  }

  private List<ChatMessageDocument> createHistory(String userMsg, String assistantMsg) {
    UUID sessionId = UUID.randomUUID();
    List<ChatMessageDocument> messages = new ArrayList<>();
    messages.add(
        createMessageDoc(
            sessionId, MessageRole.ASSISTANT.name(), assistantMsg, System.currentTimeMillis()));
    messages.add(
        createMessageDoc(
            sessionId, MessageRole.USER.name(), userMsg, System.currentTimeMillis() - 1000));
    return messages;
  }

  private ChatMessageDocument createMessageDoc(
      UUID sessionId, String role, String content, long timestamp) {
    return ChatMessageDocument.builder()
        .id(UUID.randomUUID().toString())
        .sessionId(sessionId)
        .role(role)
        .content(content)
        .timestamp(timestamp)
        .tokenCount(content.length() / 4)
        .build();
  }
}
