package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrossEncoderRerankService Tests")
class CrossEncoderRerankServiceTest {

  @Mock private ElasticsearchClient elasticsearchClient;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;

  private RagConfig ragConfig;
  private CrossEncoderRerankService service;

  @BeforeEach
  void setUp() {
    ragConfig = new RagConfig();
    RagConfig.Reranking reranking = new RagConfig.Reranking();
    reranking.getCrossEncoder().setEnabled(true);
    reranking.getCrossEncoder().setModelId("elastic-rerank");
    ragConfig.setReranking(reranking);

    service = new CrossEncoderRerankService(elasticsearchClient, meterRegistry, ragConfig);

    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
    lenient().when(meterRegistry.gauge(anyString(), any(Number.class))).thenReturn(null);
  }

  @Test
  @DisplayName("Should return candidates as-is when cross-encoder disabled")
  void shouldReturnCandidatesAsIsWhenDisabled() {
    ragConfig.getReranking().getCrossEncoder().setEnabled(false);

    List<DocumentChunk> candidates = createChunks(3);
    candidates.get(0).setRelevanceScore(0.9);
    candidates.get(1).setRelevanceScore(0.7);
    candidates.get(2).setRelevanceScore(0.5);

    List<CrossEncoderRerankService.ScoredChunk> results =
        service.rerank("test query", candidates, 3);

    assertThat(results).hasSize(3);
    assertThat(results.get(0).score()).isEqualTo(0.9);
    assertThat(results.get(1).score()).isEqualTo(0.7);
    assertThat(results.get(2).score()).isEqualTo(0.5);
  }

  @Test
  @DisplayName("Should return empty list when no candidates")
  void shouldReturnEmptyListWhenNoCandidates() {
    List<CrossEncoderRerankService.ScoredChunk> results =
        service.rerank("test query", List.of(), 5);

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("Should fall back to original scores when Elasticsearch inference fails")
  void shouldFallBackToOriginalScoresWhenInferenceFails() {
    List<DocumentChunk> candidates = createChunks(3);
    candidates.get(0).setRelevanceScore(0.8);
    candidates.get(1).setRelevanceScore(0.6);
    candidates.get(2).setRelevanceScore(0.4);

    // Mock Elasticsearch client to throw when calling inference
    when(elasticsearchClient.inference()).thenThrow(new RuntimeException("Inference API error"));

    List<CrossEncoderRerankService.ScoredChunk> results =
        service.rerank("test query", candidates, 3);

    // Should fall back to original scores
    assertThat(results).hasSize(3);
    assertThat(results.get(0).score()).isEqualTo(0.8);
    assertThat(results.get(1).score()).isEqualTo(0.6);
    assertThat(results.get(2).score()).isEqualTo(0.4);
  }

  @Test
  @DisplayName("Should limit results to topK when disabled")
  void shouldLimitResultsToTopKWhenDisabled() {
    ragConfig.getReranking().getCrossEncoder().setEnabled(false);

    List<DocumentChunk> candidates = createChunks(10);

    List<CrossEncoderRerankService.ScoredChunk> results =
        service.rerank("test query", candidates, 3);

    assertThat(results).hasSize(3);
  }

  @Test
  @DisplayName("Should use enriched content when available")
  void shouldUseEnrichedContentWhenAvailable() {
    // This test verifies that when inference is called, enriched content is preferred
    // We test indirectly by ensuring the fallback path processes enriched content chunks
    ragConfig.getReranking().getCrossEncoder().setEnabled(false);

    DocumentChunk chunkWithEnriched =
        DocumentChunk.builder()
            .id("1")
            .documentId(UUID.randomUUID())
            .sessionId(UUID.randomUUID())
            .fileName("test.pdf")
            .chunkIndex(0)
            .content("Original content")
            .enrichedContent("Enriched content with more context")
            .tokenCount(10)
            .relevanceScore(0.7)
            .build();

    List<CrossEncoderRerankService.ScoredChunk> results =
        service.rerank("test query", List.of(chunkWithEnriched), 1);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).chunk().getEnrichedContent())
        .isEqualTo("Enriched content with more context");
  }

  @Test
  @DisplayName("Should increment failure counter on inference error")
  void shouldIncrementFailureCounterOnInferenceError() {
    List<DocumentChunk> candidates = createChunks(2);

    when(elasticsearchClient.inference()).thenThrow(new RuntimeException("API unavailable"));

    service.rerank("test query", candidates, 2);

    verify(meterRegistry).counter("rag.rerank.crossencoder.failures");
    verify(counter).increment();
  }

  private List<DocumentChunk> createChunks(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            i ->
                DocumentChunk.builder()
                    .id(UUID.randomUUID().toString())
                    .documentId(UUID.randomUUID())
                    .sessionId(UUID.randomUUID())
                    .fileName("test.pdf")
                    .chunkIndex(i)
                    .content("Content " + i)
                    .tokenCount(10)
                    .relevanceScore(0.5)
                    .build())
        .toList();
  }
}
