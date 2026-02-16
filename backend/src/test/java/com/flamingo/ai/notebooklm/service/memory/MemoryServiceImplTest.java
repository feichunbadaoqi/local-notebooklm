package com.flamingo.ai.notebooklm.service.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.agent.MemoryExtractionAgent;
import com.flamingo.ai.notebooklm.agent.dto.ExtractedMemory;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.Memory;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.repository.MemoryRepository;
import com.flamingo.ai.notebooklm.elasticsearch.MemoryDocument;
import com.flamingo.ai.notebooklm.service.session.SessionService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryServiceImplTest {

  @Mock private MemoryRepository memoryRepository;
  @Mock private SessionService sessionService;
  @Mock private MemoryExtractionAgent agent;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;
  @Mock private com.flamingo.ai.notebooklm.elasticsearch.MemoryIndexService memoryIndexService;
  @Mock private com.flamingo.ai.notebooklm.service.rag.EmbeddingService embeddingService;
  @Mock private MemoryHybridSearchService memoryHybridSearchService;

  private RagConfig ragConfig;
  private MemoryServiceImpl memoryService;

  private UUID sessionId;
  private UUID memoryId;
  private Session session;

  @BeforeEach
  void setUp() {
    ragConfig = new RagConfig();
    ragConfig.setMemory(new RagConfig.Memory());

    memoryService =
        new MemoryServiceImpl(
            memoryRepository,
            sessionService,
            agent,
            ragConfig,
            meterRegistry,
            memoryIndexService,
            embeddingService,
            memoryHybridSearchService);

    sessionId = UUID.randomUUID();
    memoryId = UUID.randomUUID();
    session = Session.builder().id(sessionId).title("Test Session").build();

    // Lenient stubbing for metrics - used in various tests
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);

    // Lenient stubbing for embedding service and index service - used when saving memories
    lenient().when(embeddingService.embedText(anyString())).thenReturn(List.of(0.1f, 0.2f, 0.3f));
    lenient()
        .doNothing()
        .when(memoryIndexService)
        .indexMemories(any()); // Using doNothing for void method
  }

  @Nested
  @DisplayName("getRelevantMemories")
  class GetRelevantMemoriesTests {

    @Test
    @DisplayName("should return relevant memories using hybrid search")
    void shouldReturnRelevantMemoriesUsingHybridSearch() {
      Memory memory1 = createMemory("Fact 1", Memory.TYPE_FACT, 0.9f);
      Memory memory2 = createMemory("Fact 2", Memory.TYPE_FACT, 0.7f);

      MemoryDocument doc1 =
          MemoryDocument.builder()
              .id(memory1.getId().toString())
              .sessionId(sessionId)
              .memoryContent("Fact 1")
              .build();
      MemoryDocument doc2 =
          MemoryDocument.builder()
              .id(memory2.getId().toString())
              .sessionId(sessionId)
              .memoryContent("Fact 2")
              .build();

      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryHybridSearchService.search(sessionId, "query", 5)).thenReturn(List.of(doc1, doc2));
      when(memoryRepository.findById(memory1.getId())).thenReturn(Optional.of(memory1));
      when(memoryRepository.findById(memory2.getId())).thenReturn(Optional.of(memory2));

      List<Memory> result = memoryService.getRelevantMemories(sessionId, "query", 5);

      assertThat(result).hasSize(2);
      verify(memoryRepository).saveAll(any());
      verify(counter).increment(2);
    }

    @Test
    @DisplayName("should return empty list when no memories exist")
    void shouldReturnEmptyListWhenNoMemories() {
      when(sessionService.getSession(sessionId)).thenReturn(session);
      when(memoryHybridSearchService.search(sessionId, "query", 5)).thenReturn(List.of());

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
  @DisplayName("extractAndSaveAsync")
  class ExtractAndSaveAsyncTests {

    @Test
    @DisplayName("should not extract when memory is disabled")
    void shouldNotExtractWhenMemoryDisabled() {
      ragConfig.getMemory().setEnabled(false);

      memoryService.extractAndSaveAsync(
          sessionId, "user message", "assistant response", InteractionMode.EXPLORING);

      verify(agent, never()).extract(anyString(), anyString());
    }

    @Test
    @DisplayName("should extract and save memories from valid response")
    void shouldExtractAndSaveMemoriesFromValidResponse() {
      ExtractedMemory extracted = new ExtractedMemory("fact", "The deadline is March 15th", 0.8f);

      when(agent.extract(anyString(), anyString())).thenReturn(List.of(extracted));
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

      ExtractedMemory extracted = new ExtractedMemory("fact", "Low importance fact", 0.2f);

      when(agent.extract(anyString(), anyString())).thenReturn(List.of(extracted));
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
      ExtractedMemory extracted =
          new ExtractedMemory("fact", "Existing fact about something new", 0.8f);

      when(agent.extract(anyString(), anyString())).thenReturn(List.of(extracted));
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
      when(agent.extract(anyString(), anyString())).thenReturn(List.of());

      memoryService.extractAndSaveAsync(sessionId, "query", "response", InteractionMode.EXPLORING);

      verify(memoryRepository, never()).save(any(Memory.class));
      verify(sessionService, never()).getSession(any());
    }

    @Test
    @DisplayName("should handle agent exceptions gracefully")
    void shouldHandleAgentExceptionsGracefully() {
      when(agent.extract(anyString(), anyString())).thenThrow(new RuntimeException("Agent error"));

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
