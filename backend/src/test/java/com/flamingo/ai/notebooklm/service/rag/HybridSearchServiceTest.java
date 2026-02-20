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
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunkIndexService;
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

  @Mock private DocumentChunkIndexService documentChunkIndexService;
  @Mock private EmbeddingService embeddingService;
  @Mock private DiversityReranker diversityReranker;
  @Mock private LLMReranker llmReranker;
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
            documentChunkIndexService,
            embeddingService,
            diversityReranker,
            llmReranker,
            ragConfig,
            meterRegistry);

    sessionId = UUID.randomUUID();

    // Common mock setup for metrics
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
    lenient().when(meterRegistry.timer(anyString())).thenReturn(timer);
  }

  @Nested
  @DisplayName("search")
  class SearchTests {

    @BeforeEach
    void setUpSearch() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);
      }
    }

    @Test
    @DisplayName("should use application-side RRF combining vector and keyword search")
    void shouldUseApplicationSideRrfHybridSearch() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);
        DocumentChunk chunk1 = createChunk("c1", "Hybrid result 1");
        DocumentChunk chunk2 = createChunk("c2", "Hybrid result 2");
        DocumentChunk chunk3 = createChunk("c3", "Hybrid result 3");

        when(embeddingService.embedQuery(anyString())).thenReturn(embedding);
        when(documentChunkIndexService.vectorSearch(eq(sessionId), eq(embedding), anyInt()))
            .thenReturn(List.of(chunk1, chunk2));
        when(documentChunkIndexService.keywordSearch(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of(chunk2, chunk3));
        when(llmReranker.rerank(anyString(), any(), anyInt()))
            .thenAnswer(
                invocation -> {
                  List<DocumentChunk> chunks = invocation.getArgument(1);
                  return chunks.stream().map(c -> new LLMReranker.ScoredChunk(c, 0.8)).toList();
                });
        when(diversityReranker.rerank(any(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<DocumentChunk> results =
            hybridSearchService.search(sessionId, "test query", InteractionMode.EXPLORING);

        assertThat(results).isNotEmpty();
        verify(embeddingService).embedQuery("test query");
        verify(documentChunkIndexService).vectorSearch(eq(sessionId), eq(embedding), anyInt());
        verify(documentChunkIndexService).keywordSearch(eq(sessionId), eq("test query"), anyInt());
        verify(llmReranker).rerank(anyString(), any(), anyInt());
        verify(diversityReranker).rerank(any(), anyInt());
      }
    }

    @Test
    @DisplayName("should merge results from vector and keyword search via application-side RRF")
    void shouldMergeVectorAndKeywordResultsViaRrf() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);
        DocumentChunk vectorChunk = createChunk("v1", "Vector result");
        DocumentChunk keywordChunk = createChunk("k1", "Keyword result");

        when(embeddingService.embedQuery(anyString())).thenReturn(embedding);
        when(documentChunkIndexService.vectorSearch(eq(sessionId), eq(embedding), anyInt()))
            .thenReturn(List.of(vectorChunk));
        when(documentChunkIndexService.keywordSearch(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of(keywordChunk));
        when(llmReranker.rerank(anyString(), any(), anyInt()))
            .thenAnswer(
                invocation -> {
                  List<DocumentChunk> chunks = invocation.getArgument(1);
                  return chunks.stream().map(c -> new LLMReranker.ScoredChunk(c, 0.7)).toList();
                });
        when(diversityReranker.rerank(any(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<DocumentChunk> results =
            hybridSearchService.search(sessionId, "test query", InteractionMode.EXPLORING);

        assertThat(results).isNotEmpty();
        verify(documentChunkIndexService).vectorSearch(eq(sessionId), eq(embedding), anyInt());
        verify(documentChunkIndexService).keywordSearch(eq(sessionId), anyString(), anyInt());
      }
    }

    @Test
    @DisplayName("should handle empty search results")
    void shouldHandleEmptySearchResults() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);

        when(embeddingService.embedQuery(anyString())).thenReturn(embedding);
        when(documentChunkIndexService.vectorSearch(eq(sessionId), eq(embedding), anyInt()))
            .thenReturn(List.of());
        when(documentChunkIndexService.keywordSearch(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of());
        when(llmReranker.rerank(anyString(), any(), anyInt())).thenReturn(List.of());
        when(diversityReranker.rerank(any(), anyInt())).thenReturn(List.of());

        List<DocumentChunk> results =
            hybridSearchService.search(sessionId, "query", InteractionMode.EXPLORING);

        assertThat(results).isEmpty();
      }
    }

    @Test
    @DisplayName("should fall back to keyword-only when embedding fails")
    void shouldFallBackToKeywordOnlyWhenEmbeddingFails() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        DocumentChunk keywordChunk = createChunk("k1", "Keyword result");

        // Embedding fails (returns empty)
        when(embeddingService.embedQuery(anyString())).thenReturn(List.of());
        when(documentChunkIndexService.keywordSearch(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of(keywordChunk));

        List<DocumentChunk> results =
            hybridSearchService.search(sessionId, "query", InteractionMode.EXPLORING);

        assertThat(results).containsExactly(keywordChunk);
        verify(documentChunkIndexService).keywordSearch(eq(sessionId), anyString(), anyInt());
      }
    }
  }

  @Nested
  @DisplayName("searchWithDetails anchored")
  class AnchoredSearchTests {

    @Test
    @DisplayName("should produce same results when anchor list is empty")
    void shouldProduceSameResults_whenAnchorListEmpty() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);
        DocumentChunk chunk1 = createChunk("c1", "Content 1");
        DocumentChunk chunk2 = createChunk("c2", "Content 2");

        when(embeddingService.embedQuery(anyString())).thenReturn(embedding);
        when(documentChunkIndexService.vectorSearch(eq(sessionId), eq(embedding), anyInt()))
            .thenReturn(List.of(chunk1, chunk2));
        when(documentChunkIndexService.keywordSearch(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of(chunk1, chunk2));
        when(llmReranker.rerank(anyString(), any(), anyInt()))
            .thenAnswer(
                invocation -> {
                  List<DocumentChunk> chunks = invocation.getArgument(1);
                  return chunks.stream().map(c -> new LLMReranker.ScoredChunk(c, 0.8)).toList();
                });
        when(diversityReranker.rerank(any(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        HybridSearchService.SearchResult noAnchors =
            hybridSearchService.searchWithDetails(sessionId, "test", InteractionMode.EXPLORING);
        HybridSearchService.SearchResult emptyAnchors =
            hybridSearchService.searchWithDetails(
                sessionId, "test", InteractionMode.EXPLORING, List.of());

        assertThat(emptyAnchors.finalResults()).hasSameSizeAs(noAnchors.finalResults());
      }
    }

    @Test
    @DisplayName("should boost anchor document chunks above others in final ranking")
    void shouldBoostAnchorChunks_aboveOthers() {
      try (var timerMock =
          org.mockito.Mockito.mockStatic(Timer.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
        timerMock.when(() -> Timer.start(meterRegistry)).thenReturn(timerSample);

        UUID anchorDocId = UUID.randomUUID();
        UUID otherDocId = UUID.randomUUID();

        // The anchor chunk is ranked LOWER in raw search results
        DocumentChunk anchorChunk =
            DocumentChunk.builder()
                .id("anchor")
                .sessionId(sessionId)
                .documentId(anchorDocId)
                .content("Anchor doc content")
                .fileName("anchor.pdf")
                .chunkIndex(0)
                .build();
        DocumentChunk otherChunk =
            DocumentChunk.builder()
                .id("other")
                .sessionId(sessionId)
                .documentId(otherDocId)
                .content("Other doc content")
                .fileName("other.pdf")
                .chunkIndex(0)
                .build();

        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);
        when(embeddingService.embedQuery(anyString())).thenReturn(embedding);
        when(documentChunkIndexService.vectorSearch(eq(sessionId), eq(embedding), anyInt()))
            .thenReturn(List.of(otherChunk, anchorChunk));
        when(documentChunkIndexService.keywordSearch(eq(sessionId), anyString(), anyInt()))
            .thenReturn(List.of(otherChunk, anchorChunk));
        when(llmReranker.rerank(anyString(), any(), anyInt()))
            .thenAnswer(
                invocation -> {
                  List<DocumentChunk> chunks = invocation.getArgument(1);
                  return chunks.stream().map(c -> new LLMReranker.ScoredChunk(c, 0.8)).toList();
                });
        when(diversityReranker.rerank(any(), anyInt()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Use high boost to guarantee anchor wins
        RagConfig.Retrieval retrieval = new RagConfig.Retrieval();
        retrieval.setSourceAnchoringEnabled(true);
        retrieval.setSourceAnchoringBoost(0.5);
        ragConfig.setRetrieval(retrieval);

        List<DocumentChunk> results =
            hybridSearchService
                .searchWithDetails(
                    sessionId,
                    "test query",
                    InteractionMode.EXPLORING,
                    List.of(anchorDocId.toString()))
                .finalResults();

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getId()).isEqualTo("anchor");
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
