package com.flamingo.ai.notebooklm.service.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.Memory;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.repository.MemoryRepository;
import com.flamingo.ai.notebooklm.exception.MemoryNotFoundException;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MemoryServiceImplTest {

  @Mock private MemoryRepository memoryRepository;
  @Mock private SessionService sessionService;
  @Mock private ChatModel chatModel;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;

  private RagConfig ragConfig;
  private ObjectMapper objectMapper;
  private MemoryServiceImpl memoryService;

  private UUID sessionId;
  private UUID memoryId;
  private Session session;

  @BeforeEach
  void setUp() {
    ragConfig = new RagConfig();
    ragConfig.setMemory(new RagConfig.Memory());
    objectMapper = new ObjectMapper();

    memoryService =
        new MemoryServiceImpl(
            memoryRepository, sessionService, chatModel, ragConfig, meterRegistry, objectMapper);

    sessionId = UUID.randomUUID();
    memoryId = UUID.randomUUID();
    session = Session.builder().id(sessionId).title("Test Session").build();

    // Lenient stubbing for metrics - used in various tests
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
  }

  @Nested
  @DisplayName("getRelevantMemories")
  class GetRelevantMemoriesTests {

    @Test
    @DisplayName("should return top memories by importance")
    void shouldReturnTopMemoriesByImportance() {
      Memory memory1 = createMemory("Fact 1", Memory.TYPE_FACT, 0.9f);
      Memory memory2 = createMemory("Fact 2", Memory.TYPE_FACT, 0.7f);

      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryRepository.findTopMemoriesBySessionId(eq(sessionId), any(PageRequest.class)))
          .thenReturn(List.of(memory1, memory2));

      List<Memory> result = memoryService.getRelevantMemories(sessionId, "query", 5);

      assertThat(result).hasSize(2);
      verify(memoryRepository).saveAll(any());
      verify(counter).increment(2);
    }

    @Test
    @DisplayName("should return empty list when no memories exist")
    void shouldReturnEmptyListWhenNoMemories() {
      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryRepository.findTopMemoriesBySessionId(eq(sessionId), any(PageRequest.class)))
          .thenReturn(List.of());

      List<Memory> result = memoryService.getRelevantMemories(sessionId, "query", 5);

      assertThat(result).isEmpty();
      verify(memoryRepository, never()).saveAll(any());
    }
  }

  @Nested
  @DisplayName("buildMemoryContext")
  class BuildMemoryContextTests {

    @Test
    @DisplayName("should format memories correctly")
    void shouldFormatMemoriesCorrectly() {
      Memory fact = createMemory("Project deadline is March 15th", Memory.TYPE_FACT, 0.9f);
      Memory preference = createMemory("User prefers bullet points", Memory.TYPE_PREFERENCE, 0.7f);

      String context = memoryService.buildMemoryContext(List.of(fact, preference));

      assertThat(context).contains("Relevant memories from this session:");
      assertThat(context).contains("[FACT] Project deadline is March 15th (importance: 0.9)");
      assertThat(context).contains("[PREFERENCE] User prefers bullet points (importance: 0.7)");
      assertThat(context).contains("Use these memories to provide contextually aware responses.");
    }

    @Test
    @DisplayName("should return empty string for empty list")
    void shouldReturnEmptyStringForEmptyList() {
      String context = memoryService.buildMemoryContext(List.of());
      assertThat(context).isEmpty();
    }
  }

  @Nested
  @DisplayName("getAllMemories")
  class GetAllMemoriesTests {

    @Test
    @DisplayName("should return all memories ordered by importance")
    void shouldReturnAllMemoriesOrderedByImportance() {
      Memory memory1 = createMemory("High importance", Memory.TYPE_FACT, 0.9f);
      Memory memory2 = createMemory("Low importance", Memory.TYPE_INSIGHT, 0.3f);

      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryRepository.findBySessionIdOrderByImportanceDesc(sessionId))
          .thenReturn(List.of(memory1, memory2));

      List<Memory> result = memoryService.getAllMemories(sessionId);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getImportance()).isGreaterThan(result.get(1).getImportance());
    }
  }

  @Nested
  @DisplayName("getMemory")
  class GetMemoryTests {

    @Test
    @DisplayName("should return memory when found")
    void shouldReturnMemoryWhenFound() {
      Memory memory = createMemory("Test memory", Memory.TYPE_FACT, 0.5f);
      when(memoryRepository.findById(memoryId)).thenReturn(Optional.of(memory));

      Memory result = memoryService.getMemory(memoryId);

      assertThat(result).isEqualTo(memory);
    }

    @Test
    @DisplayName("should throw exception when memory not found")
    void shouldThrowExceptionWhenMemoryNotFound() {
      when(memoryRepository.findById(memoryId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> memoryService.getMemory(memoryId))
          .isInstanceOf(MemoryNotFoundException.class)
          .hasMessageContaining(memoryId.toString());
    }
  }

  @Nested
  @DisplayName("deleteMemory")
  class DeleteMemoryTests {

    @Test
    @DisplayName("should delete memory when exists")
    void shouldDeleteMemoryWhenExists() {
      when(memoryRepository.existsById(memoryId)).thenReturn(true);

      memoryService.deleteMemory(memoryId);

      verify(memoryRepository).deleteById(memoryId);
    }

    @Test
    @DisplayName("should throw exception when memory not found")
    void shouldThrowExceptionWhenMemoryNotFound() {
      when(memoryRepository.existsById(memoryId)).thenReturn(false);

      assertThatThrownBy(() -> memoryService.deleteMemory(memoryId))
          .isInstanceOf(MemoryNotFoundException.class);

      verify(memoryRepository, never()).deleteById(any());
    }
  }

  @Nested
  @DisplayName("addMemory")
  class AddMemoryTests {

    @Test
    @DisplayName("should create memory with valid type")
    void shouldCreateMemoryWithValidType() {
      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryRepository.save(any(Memory.class)))
          .thenAnswer(inv -> inv.getArgument(0, Memory.class));
      when(memoryRepository.countBySessionId(sessionId)).thenReturn(1L);

      Memory result = memoryService.addMemory(sessionId, "Test content", "fact", 0.8f);

      assertThat(result.getMemoryContent()).isEqualTo("Test content");
      assertThat(result.getMemoryType()).isEqualTo("fact");
      assertThat(result.getImportance()).isEqualTo(0.8f);
    }

    @Test
    @DisplayName("should normalize memory type to lowercase")
    void shouldNormalizeMemoryTypeToLowercase() {
      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryRepository.save(any(Memory.class)))
          .thenAnswer(inv -> inv.getArgument(0, Memory.class));
      when(memoryRepository.countBySessionId(sessionId)).thenReturn(1L);

      Memory result = memoryService.addMemory(sessionId, "Test", "PREFERENCE", 0.5f);

      assertThat(result.getMemoryType()).isEqualTo("preference");
    }

    @Test
    @DisplayName("should clamp importance to valid range")
    void shouldClampImportanceToValidRange() {
      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryRepository.save(any(Memory.class)))
          .thenAnswer(inv -> inv.getArgument(0, Memory.class));
      when(memoryRepository.countBySessionId(sessionId)).thenReturn(1L);

      Memory result = memoryService.addMemory(sessionId, "Test", "fact", 1.5f);
      assertThat(result.getImportance()).isEqualTo(1.0f);

      result = memoryService.addMemory(sessionId, "Test", "fact", -0.5f);
      assertThat(result.getImportance()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("should throw exception for invalid memory type")
    void shouldThrowExceptionForInvalidMemoryType() {
      when(sessionService.getSession(sessionId)).thenReturn(session);

      assertThatThrownBy(() -> memoryService.addMemory(sessionId, "Test", "invalid", 0.5f))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid memory type");
    }

    @Test
    @DisplayName("should use default importance when null")
    void shouldUseDefaultImportanceWhenNull() {
      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryRepository.save(any(Memory.class)))
          .thenAnswer(inv -> inv.getArgument(0, Memory.class));
      when(memoryRepository.countBySessionId(sessionId)).thenReturn(1L);

      Memory result = memoryService.addMemory(sessionId, "Test", "fact", null);

      assertThat(result.getImportance()).isEqualTo(0.5f);
    }
  }

  @Nested
  @DisplayName("extractAndSaveAsync")
  class ExtractAndSaveAsyncTests {

    @Test
    @DisplayName("should not extract when memory is disabled")
    void shouldNotExtractWhenMemoryDisabled() {
      ragConfig.getMemory().setEnabled(false);

      memoryService.extractAndSaveAsync(
          sessionId, "user message", "assistant response", InteractionMode.EXPLORING);

      verify(chatModel, never()).chat(anyString());
    }

    @Test
    @DisplayName("should extract and save memories from valid response")
    void shouldExtractAndSaveMemoriesFromValidResponse() {
      String llmResponse =
          "[{\"type\": \"fact\", \"content\": \"The deadline is March 15th\", \"importance\": 0.8}]";

      when(chatModel.chat(anyString())).thenReturn(llmResponse);
      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryRepository.findBySessionIdOrderByImportanceDesc(sessionId)).thenReturn(List.of());
      when(memoryRepository.countBySessionId(sessionId)).thenReturn(1L);

      memoryService.extractAndSaveAsync(
          sessionId,
          "What is the deadline?",
          "The deadline is March 15th",
          InteractionMode.RESEARCH);

      ArgumentCaptor<Memory> captor = ArgumentCaptor.forClass(Memory.class);
      verify(memoryRepository).save(captor.capture());

      Memory saved = captor.getValue();
      assertThat(saved.getMemoryContent()).isEqualTo("The deadline is March 15th");
      assertThat(saved.getMemoryType()).isEqualTo("fact");
      assertThat(saved.getImportance()).isEqualTo(0.8f);
    }

    @Test
    @DisplayName("should skip memories below extraction threshold")
    void shouldSkipMemoriesBelowExtractionThreshold() {
      ragConfig.getMemory().setExtractionThreshold(0.5f);

      String llmResponse =
          "[{\"type\": \"fact\", \"content\": \"Low importance fact\", \"importance\": 0.2}]";

      when(chatModel.chat(anyString())).thenReturn(llmResponse);
      when(sessionService.getSession(sessionId)).thenReturn(session);
      // Note: findBySessionIdOrderByImportanceDesc and countBySessionId not called
      // because importance is below threshold

      memoryService.extractAndSaveAsync(sessionId, "query", "response", InteractionMode.EXPLORING);

      verify(memoryRepository, never()).save(any(Memory.class));
    }

    @Test
    @DisplayName("should update existing memory importance for similar content")
    void shouldUpdateExistingMemoryForSimilarContent() {
      // Use containment case: "Existing fact" is contained within "Existing fact about something"
      Memory existing = createMemory("Existing fact", Memory.TYPE_FACT, 0.5f);
      String llmResponse =
          "[{\"type\": \"fact\", \"content\": \"Existing fact about something new\", \"importance\": 0.8}]";

      when(chatModel.chat(anyString())).thenReturn(llmResponse);
      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryRepository.findBySessionIdOrderByImportanceDesc(sessionId))
          .thenReturn(List.of(existing));
      when(memoryRepository.countBySessionId(sessionId)).thenReturn(1L);

      memoryService.extractAndSaveAsync(sessionId, "query", "response", InteractionMode.EXPLORING);

      // Should update existing memory's importance due to containment match
      verify(memoryRepository).save(existing);
    }

    @Test
    @DisplayName("should handle empty extraction response")
    void shouldHandleEmptyExtractionResponse() {
      when(chatModel.chat(anyString())).thenReturn("[]");

      memoryService.extractAndSaveAsync(sessionId, "query", "response", InteractionMode.EXPLORING);

      verify(memoryRepository, never()).save(any(Memory.class));
      verify(sessionService, never()).getSession(any());
    }

    @Test
    @DisplayName("should handle malformed JSON gracefully")
    void shouldHandleMalformedJsonGracefully() {
      when(chatModel.chat(anyString())).thenReturn("not valid json");

      // Should not throw, just log warning
      memoryService.extractAndSaveAsync(sessionId, "query", "response", InteractionMode.EXPLORING);

      verify(memoryRepository, never()).save(any(Memory.class));
    }
  }

  private Memory createMemory(String content, String type, float importance) {
    return Memory.builder()
        .id(UUID.randomUUID())
        .session(session)
        .memoryContent(content)
        .memoryType(type)
        .importance(importance)
        .createdAt(LocalDateTime.now())
        .lastAccessedAt(LocalDateTime.now())
        .build();
  }
}
