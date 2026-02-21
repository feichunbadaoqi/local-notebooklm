package com.flamingo.ai.notebooklm.service.rag.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeiCrossEncoderReranker Tests")
class TeiCrossEncoderRerankerTest {

  @Mock private TeiRerankerClient teiRerankerClient;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;

  private TeiCrossEncoderReranker reranker;

  @BeforeEach
  void setUp() {
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
    reranker = new TeiCrossEncoderReranker(teiRerankerClient, meterRegistry);
  }

  @Test
  @DisplayName("Should rerank candidates by TEI scores")
  void shouldRerankCandidatesByTeiScores() {
    List<DocumentChunk> candidates = new ArrayList<>();
    candidates.add(createChunk("Low relevance content"));
    candidates.add(createChunk("High relevance content about machine learning"));
    candidates.add(createChunk("Medium relevance content"));

    when(teiRerankerClient.rerank(anyString(), anyList()))
        .thenReturn(
            List.of(
                new TeiRerankerClient.RerankResult(1, 0.95),
                new TeiRerankerClient.RerankResult(2, 0.60),
                new TeiRerankerClient.RerankResult(0, 0.15)));

    List<Reranker.ScoredChunk> results = reranker.rerank("machine learning", candidates, 3);

    assertThat(results).hasSize(3);
    assertThat(results.get(0).score()).isEqualTo(0.95);
    assertThat(results.get(0).chunk().getContent())
        .isEqualTo("High relevance content about machine learning");
    assertThat(results.get(1).score()).isEqualTo(0.60);
    assertThat(results.get(2).score()).isEqualTo(0.15);
  }

  @Test
  @DisplayName("Should limit results to topK")
  void shouldLimitResultsToTopK() {
    List<DocumentChunk> candidates = createChunks(5);

    when(teiRerankerClient.rerank(anyString(), anyList()))
        .thenReturn(
            List.of(
                new TeiRerankerClient.RerankResult(0, 0.9),
                new TeiRerankerClient.RerankResult(1, 0.8),
                new TeiRerankerClient.RerankResult(2, 0.7),
                new TeiRerankerClient.RerankResult(3, 0.6),
                new TeiRerankerClient.RerankResult(4, 0.5)));

    List<Reranker.ScoredChunk> results = reranker.rerank("test", candidates, 3);

    assertThat(results).hasSize(3);
    assertThat(results.get(0).score()).isEqualTo(0.9);
    assertThat(results.get(2).score()).isEqualTo(0.7);
  }

  @Test
  @DisplayName("Should handle empty candidates list")
  void shouldHandleEmptyCandidatesList() {
    List<Reranker.ScoredChunk> results = reranker.rerank("test", List.of(), 5);
    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("Should sort results by score descending")
  void shouldSortResultsByScoreDescending() {
    List<DocumentChunk> candidates = createChunks(3);

    // TEI returns results in arbitrary order
    when(teiRerankerClient.rerank(anyString(), anyList()))
        .thenReturn(
            List.of(
                new TeiRerankerClient.RerankResult(0, 0.3),
                new TeiRerankerClient.RerankResult(1, 0.9),
                new TeiRerankerClient.RerankResult(2, 0.6)));

    List<Reranker.ScoredChunk> results = reranker.rerank("test", candidates, 3);

    assertThat(results.get(0).score()).isEqualTo(0.9);
    assertThat(results.get(1).score()).isEqualTo(0.6);
    assertThat(results.get(2).score()).isEqualTo(0.3);
  }

  @Test
  @DisplayName("Fallback should return candidates with RRF scores")
  void fallbackShouldReturnCandidatesWithRrfScores() {
    List<DocumentChunk> candidates = createChunks(3);
    candidates.get(0).setRelevanceScore(0.03);
    candidates.get(1).setRelevanceScore(0.02);
    candidates.get(2).setRelevanceScore(0.01);

    List<Reranker.ScoredChunk> results =
        reranker.rerankFallback("test", candidates, 3, new RuntimeException("TEI unavailable"));

    assertThat(results).hasSize(3);
    assertThat(results.get(0).score()).isEqualTo(0.03);
    assertThat(results.get(1).score()).isEqualTo(0.02);
    assertThat(results.get(2).score()).isEqualTo(0.01);
  }

  @Test
  @DisplayName("Fallback should respect topK limit")
  void fallbackShouldRespectTopKLimit() {
    List<DocumentChunk> candidates = createChunks(5);
    for (int i = 0; i < candidates.size(); i++) {
      candidates.get(i).setRelevanceScore(0.5 - i * 0.1);
    }

    List<Reranker.ScoredChunk> results =
        reranker.rerankFallback("test", candidates, 3, new RuntimeException("TEI down"));

    assertThat(results).hasSize(3);
  }

  private List<DocumentChunk> createChunks(int count) {
    List<DocumentChunk> chunks = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      chunks.add(createChunk("Content " + i));
    }
    return chunks;
  }

  private DocumentChunk createChunk(String content) {
    return DocumentChunk.builder()
        .id(UUID.randomUUID().toString())
        .documentId(UUID.randomUUID())
        .sessionId(UUID.randomUUID())
        .fileName("test.pdf")
        .chunkIndex(0)
        .content(content)
        .tokenCount(10)
        .relevanceScore(0.5)
        .build();
  }
}
