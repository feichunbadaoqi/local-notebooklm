package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.elasticsearch.ElasticsearchIndexService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

  @Mock private ElasticsearchIndexService elasticsearchIndexService;
  @Mock private EmbeddingService embeddingService;
  @Mock private DiversityReranker diversityReranker;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;
  @Mock private Timer timer;
  @Mock private Timer.Sample timerSample;

  private RagConfig ragConfig;
  private HybridSearchService hybridSearchService;

  private UUID sessionId;

  @BeforeEach
  void setUp() {
    ragConfig = new RagConfig();
    ragConfig.setRetrieval(new RagConfig.Retrieval());
    ragConfig.setDiversity(new RagConfig.Diversity());

    hybridSearchService =
        new HybridSearchService(
            elasticsearchIndexService,
            embeddingService,
            diversityReranker,
            ragConfig,
            meterRegistry);

    sessionId = UUID.randomUUID();

    // Common mock setup for metrics
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
    lenient().when(meterRegistry.timer(anyString())).thenReturn(timer);
  }

  private void setupTimerMock() {
    try (var mockedTimer = org.mockito.MockedStatic.class.cast(null)) {
      // Timer.start is a static method, we'll mock the instance method instead
    }
  }

  @Nested
  @DisplayName("search")
  class SearchTests {

    @BeforeEach
    void setUpSearch() {
      // Mock Timer.start static method
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);
      }
    }

    @Test
    @DisplayName("should combine vector and keyword search results using RRF")
    void shouldCombineVectorAndKeywordSearchUsingRrf() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);
        DocumentChunk vectorChunk1 = createChunk("v1", "Vector result 1");
        DocumentChunk vectorChunk2 = createChunk("v2", "Vector result 2");
        DocumentChunk keywordChunk1 = createChunk("k1", "Keyword result 1");
        DocumentChunk keywordChunk2 = createChunk("v1", "Vector result 1"); // Same as vector

        when(embeddingService.embedText(anyString())).thenReturn(embedding);
        when(elasticsearchIndexService.vectorSearch(eq(sessionId), eq(embedding), anyInt()))
            .thenReturn(List.of(vectorChunk1, vectorChunk2));
        when(elasticsearchIndexService.keywordSearch(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of(keywordChunk1, keywordChunk2));
        when(diversityReranker.rerank(any(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<DocumentChunk> results =
            hybridSearchService.search(sessionId, "test query", InteractionMode.EXPLORING);

        assertThat(results).isNotEmpty();
        verify(embeddingService).embedText("test query");
        verify(elasticsearchIndexService).vectorSearch(eq(sessionId), eq(embedding), anyInt());
        verify(elasticsearchIndexService).keywordSearch(eq(sessionId), eq("test query"), anyInt());
      }
    }

    @Test
    @DisplayName("should handle empty search results")
    void shouldHandleEmptySearchResults() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);

        when(embeddingService.embedText(anyString())).thenReturn(embedding);
        when(elasticsearchIndexService.vectorSearch(eq(sessionId), any(), anyInt()))
            .thenReturn(List.of());
        when(elasticsearchIndexService.keywordSearch(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of());
        when(diversityReranker.rerank(any(), anyInt())).thenReturn(List.of());

        List<DocumentChunk> results =
            hybridSearchService.search(sessionId, "query", InteractionMode.EXPLORING);

        assertThat(results).isEmpty();
      }
    }

    @Test
    @DisplayName("should deduplicate results from both searches")
    void shouldDeduplicateResults() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);
        String sharedId = "shared-chunk";

        DocumentChunk vectorChunk = createChunk(sharedId, "Shared content");
        DocumentChunk keywordChunk = createChunk(sharedId, "Shared content");

        when(embeddingService.embedText(anyString())).thenReturn(embedding);
        when(elasticsearchIndexService.vectorSearch(eq(sessionId), any(), anyInt()))
            .thenReturn(List.of(vectorChunk));
        when(elasticsearchIndexService.keywordSearch(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of(keywordChunk));
        when(diversityReranker.rerank(any(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<DocumentChunk> results =
            hybridSearchService.search(sessionId, "query", InteractionMode.EXPLORING);

        // Should only have one instance of the shared chunk (deduplicated)
        long sharedCount = results.stream().filter(c -> c.getId().equals(sharedId)).count();
        assertThat(sharedCount).isLessThanOrEqualTo(1);
      }
    }
  }

  @Nested
  @DisplayName("buildContext")
  class BuildContextTests {

    @Test
    @DisplayName("should format chunks as context string")
    void shouldFormatChunksAsContextString() {
      DocumentChunk chunk1 = createChunk("1", "First chunk content");
      DocumentChunk chunk2 = createChunk("2", "Second chunk content");
      chunk1.setFileName("doc1.pdf");
      chunk2.setFileName("doc2.pdf");

      String context = hybridSearchService.buildContext(List.of(chunk1, chunk2));

      assertThat(context).contains("DOCUMENT CONTEXT");
      assertThat(context).contains("[Source 1: doc1.pdf");
      assertThat(context).contains("First chunk content");
      assertThat(context).contains("[Source 2: doc2.pdf");
      assertThat(context).contains("Second chunk content");
    }

    @Test
    @DisplayName("should return empty string for empty list")
    void shouldReturnEmptyStringForEmptyList() {
      String context = hybridSearchService.buildContext(List.of());
      assertThat(context).isEmpty();
    }

    @Test
    @DisplayName("should number chunks in context")
    void shouldNumberChunksInContext() {
      DocumentChunk chunk1 = createChunk("1", "Content 1");
      DocumentChunk chunk2 = createChunk("2", "Content 2");
      chunk1.setFileName("doc.pdf");
      chunk2.setFileName("doc.pdf");

      String context = hybridSearchService.buildContext(List.of(chunk1, chunk2));

      assertThat(context).contains("[Source 1:");
      assertThat(context).contains("[Source 2:");
    }
  }

  private DocumentChunk createChunk(String id, String content) {
    return DocumentChunk.builder()
        .id(id)
        .sessionId(sessionId)
        .documentId(UUID.randomUUID())
        .content(content)
        .fileName("test.pdf")
        .chunkIndex(0)
        .tokenCount(content.length() / 4)
        .build();
  }
}
