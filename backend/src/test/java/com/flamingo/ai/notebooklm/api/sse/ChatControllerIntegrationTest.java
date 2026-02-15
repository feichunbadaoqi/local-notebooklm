package com.flamingo.ai.notebooklm.api.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flamingo.ai.notebooklm.api.dto.request.ChatRequest;
import com.flamingo.ai.notebooklm.api.dto.response.StreamChunkResponse;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import com.flamingo.ai.notebooklm.service.chat.ChatCompactionService;
import com.flamingo.ai.notebooklm.service.chat.ChatService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController Integration Tests")
class ChatControllerIntegrationTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Mock private ChatService chatService;
  @Mock private ChatCompactionService compactionService;
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    ChatController chatController =
        new ChatController(chatService, compactionService, meterRegistry);
    mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("Should stream chat response successfully")
  void shouldStreamChatResponseSuccessfully() throws Exception {
    UUID sessionId = UUID.randomUUID();
    ChatRequest request = ChatRequest.builder().message("What is machine learning?").build();

    List<StreamChunkResponse> mockResponses =
        List.of(
            new StreamChunkResponse("Machine", false),
            new StreamChunkResponse(" learning", false),
            new StreamChunkResponse(" is", false),
            new StreamChunkResponse("", true));

    when(chatService.streamChat(any(UUID.class), anyString()))
        .thenReturn(Flux.fromIterable(mockResponses));

    mockMvc
        .perform(
            post("/api/sessions/{sessionId}/chat/stream", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

    verify(chatService).streamChat(sessionId, "What is machine learning?");
  }

  @Test
  @DisplayName("Should handle valid short message")
  void shouldHandleValidShortMessage() throws Exception {
    UUID sessionId = UUID.randomUUID();
    ChatRequest request = ChatRequest.builder().message("Hello").build();

    when(chatService.streamChat(any(UUID.class), anyString()))
        .thenReturn(Flux.just(new StreamChunkResponse("Hello there!", true)));

    mockMvc
        .perform(
            post("/api/sessions/{sessionId}/chat/stream", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isOk());

    verify(chatService).streamChat(sessionId, "Hello");
  }

  @Test
  @DisplayName("Should get chat history successfully")
  void shouldGetChatHistorySuccessfully() throws Exception {
    UUID sessionId = UUID.randomUUID();
    List<ChatMessage> mockMessages =
        List.of(
            createChatMessage(sessionId, MessageRole.USER, "What is AI?"),
            createChatMessage(sessionId, MessageRole.ASSISTANT, "AI is artificial intelligence."));

    when(chatService.getChatHistory(any(UUID.class), anyInt())).thenReturn(mockMessages);

    mockMvc
        .perform(get("/api/sessions/{sessionId}/messages", sessionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].content").value("What is AI?"))
        .andExpect(jsonPath("$[0].role").value("USER"))
        .andExpect(jsonPath("$[1].content").value("AI is artificial intelligence."))
        .andExpect(jsonPath("$[1].role").value("ASSISTANT"));

    verify(chatService).getChatHistory(sessionId, 50); // Default limit
  }

  @Test
  @DisplayName("Should get chat history with custom limit")
  void shouldGetChatHistoryWithCustomLimit() throws Exception {
    UUID sessionId = UUID.randomUUID();
    List<ChatMessage> mockMessages =
        List.of(createChatMessage(sessionId, MessageRole.USER, "Test message"));

    when(chatService.getChatHistory(any(UUID.class), anyInt())).thenReturn(mockMessages);

    mockMvc
        .perform(get("/api/sessions/{sessionId}/messages?limit=10", sessionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1));

    verify(chatService).getChatHistory(sessionId, 10);
  }

  @Test
  @DisplayName("Should return empty list when no chat history exists")
  void shouldReturnEmptyListWhenNoChatHistoryExists() throws Exception {
    UUID sessionId = UUID.randomUUID();

    when(chatService.getChatHistory(any(UUID.class), anyInt())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/sessions/{sessionId}/messages", sessionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Should force compaction successfully")
  void shouldForceCompactionSuccessfully() throws Exception {
    UUID sessionId = UUID.randomUUID();
    doNothing().when(compactionService).compact(any(UUID.class));

    mockMvc
        .perform(post("/api/sessions/{sessionId}/compact", sessionId))
        .andExpect(status().isOk());

    verify(compactionService).compact(sessionId);
  }

  @Test
  @DisplayName("Should handle multiple compaction requests")
  void shouldHandleMultipleCompactionRequests() throws Exception {
    UUID sessionId = UUID.randomUUID();
    doNothing().when(compactionService).compact(any(UUID.class));

    // First compaction
    mockMvc
        .perform(post("/api/sessions/{sessionId}/compact", sessionId))
        .andExpect(status().isOk());

    // Second compaction
    mockMvc
        .perform(post("/api/sessions/{sessionId}/compact", sessionId))
        .andExpect(status().isOk());

    verify(compactionService, org.mockito.Mockito.times(2)).compact(sessionId);
  }

  @Test
  @DisplayName("Should reject invalid session ID format")
  void shouldRejectInvalidSessionIdFormat() throws Exception {
    mockMvc
        .perform(get("/api/sessions/invalid-uuid/messages"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("Should stream multiple chunks in order")
  void shouldStreamMultipleChunksInOrder() throws Exception {
    UUID sessionId = UUID.randomUUID();
    ChatRequest request = ChatRequest.builder().message("Explain neural networks").build();

    List<StreamChunkResponse> mockResponses =
        List.of(
            new StreamChunkResponse("Neural", false),
            new StreamChunkResponse(" networks", false),
            new StreamChunkResponse(" are", false),
            new StreamChunkResponse(" computational", false),
            new StreamChunkResponse(" models", false),
            new StreamChunkResponse("", true));

    when(chatService.streamChat(any(UUID.class), anyString()))
        .thenReturn(Flux.fromIterable(mockResponses));

    mockMvc
        .perform(
            post("/api/sessions/{sessionId}/chat/stream", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isOk());

    verify(chatService).streamChat(sessionId, "Explain neural networks");
  }

  @Test
  @DisplayName("Should handle very long chat message")
  void shouldHandleVeryLongChatMessage() throws Exception {
    UUID sessionId = UUID.randomUUID();
    String longMessage = "a".repeat(5000);
    ChatRequest request = ChatRequest.builder().message(longMessage).build();

    when(chatService.streamChat(any(UUID.class), anyString()))
        .thenReturn(Flux.just(new StreamChunkResponse("Response", true)));

    mockMvc
        .perform(
            post("/api/sessions/{sessionId}/chat/stream", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isOk());

    verify(chatService).streamChat(sessionId, longMessage);
  }

  @Test
  @DisplayName("Should handle chat history with large limit")
  void shouldHandleChatHistoryWithLargeLimit() throws Exception {
    UUID sessionId = UUID.randomUUID();

    when(chatService.getChatHistory(any(UUID.class), anyInt())).thenReturn(List.of());

    mockMvc
        .perform(get("/api/sessions/{sessionId}/messages?limit=1000", sessionId))
        .andExpect(status().isOk());

    verify(chatService).getChatHistory(sessionId, 1000);
  }

  private ChatMessage createChatMessage(UUID sessionId, MessageRole role, String content) {
    return ChatMessage.builder()
        .id(UUID.randomUUID())
        .role(role)
        .content(content)
        .tokenCount(content.split("\\s+").length)
        .isCompacted(false)
        .createdAt(LocalDateTime.now())
        .build();
  }
}
