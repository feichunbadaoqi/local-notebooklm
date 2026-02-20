package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.agent.CrossEncoderRerankerAgent;
import com.flamingo.ai.notebooklm.agent.dto.RerankingScores;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("LLMReranker Tests")
class LLMRerankerTest {

  @Mock private CrossEncoderRerankerAgent agent;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;

  private LLMReranker reranker;

  @BeforeEach
  void setUp() {
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);

    reranker = new LLMReranker(agent, meterRegistry);
    ReflectionTestUtils.setField(reranker, "batchSize", 20);
    ReflectionTestUtils.setField(reranker, "enabled", true);
  }

  @Test
  @DisplayName("Should rerank candidates by relevance score")
  void shouldRerankCandidatesByRelevanceScore() {
    List<DocumentChunk> candidates = new ArrayList<>();
    candidates.add(createChunk("Low relevance content"));
    candidates.add(createChunk("High relevance content about machine learning"));
    candidates.add(createChunk("Medium relevance content"));

    String query = "machine learning";

    when(agent.scorePassages(anyString(), anyString()))
        .thenReturn(new RerankingScores(List.of(0.3, 0.9, 0.6)));

    List<LLMReranker.ScoredChunk> results = reranker.rerank(query, candidates, 3);

    assertThat(results).hasSize(3);
    assertThat(results.get(0).score()).isEqualTo(0.9);
    assertThat(results.get(1).score()).isEqualTo(0.6);
    assertThat(results.get(2).score()).isEqualTo(0.3);
  }

  @Test
  @DisplayName("Should limit results to topK")
  void shouldLimitResultsToTopK() {
    List<DocumentChunk> candidates = createChunks(10);
    String query = "test query";

    when(agent.scorePassages(anyString(), anyString()))
        .thenReturn(
            new RerankingScores(List.of(0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.05)));

    List<LLMReranker.ScoredChunk> results = reranker.rerank(query, candidates, 5);

    assertThat(results).hasSize(5);
    assertThat(results.get(0).score()).isEqualTo(0.9);
    assertThat(results.get(4).score()).isEqualTo(0.5);
  }

  @Test
  @DisplayName("Should handle empty candidates list")
  void shouldHandleEmptyCandidatesList() {
    List<DocumentChunk> candidates = new ArrayList<>();
    String query = "test query";

    List<LLMReranker.ScoredChunk> results = reranker.rerank(query, candidates, 5);

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("Should process candidates in batches")
  void shouldProcessCandidatesInBatches() {
    ReflectionTestUtils.setField(reranker, "batchSize", 5);

    List<DocumentChunk> candidates = createChunks(12); // Will need 3 batches (5, 5, 2)
    String query = "test query";

    when(agent.scorePassages(anyString(), anyString()))
        .thenReturn(new RerankingScores(List.of(0.9, 0.8, 0.7, 0.6, 0.5)))
        .thenReturn(new RerankingScores(List.of(0.4, 0.3, 0.2, 0.1, 0.05)))
        .thenReturn(new RerankingScores(List.of(0.95, 0.85)));

    List<LLMReranker.ScoredChunk> results = reranker.rerank(query, candidates, 12);

    assertThat(results).hasSize(12);
    assertThat(results.get(0).score()).isEqualTo(0.95);
    assertThat(results.get(1).score()).isEqualTo(0.9);
  }

  @Test
  @DisplayName("Should pad missing scores with 0.5 when fewer scores than candidates")
  void shouldPadMissingScoresWithDefault() {
    List<DocumentChunk> candidates = createChunks(5);
    String query = "test query";

    // Agent returns only 3 scores for 5 candidates
    when(agent.scorePassages(anyString(), anyString()))
        .thenReturn(new RerankingScores(List.of(0.9, 0.8, 0.7)));

    List<LLMReranker.ScoredChunk> results = reranker.rerank(query, candidates, 5);

    assertThat(results).hasSize(5);
    assertThat(results.get(0).score()).isEqualTo(0.9);
    assertThat(results.get(1).score()).isEqualTo(0.8);
    assertThat(results.get(2).score()).isEqualTo(0.7);
    assertThat(results.get(3).score()).isEqualTo(0.5);
    assertThat(results.get(4).score()).isEqualTo(0.5);
  }

  @Test
  @DisplayName("Should clamp scores to 0.0-1.0 range")
  void shouldClampScoresToValidRange() {
    List<DocumentChunk> candidates = createChunks(3);
    String query = "test query";

    when(agent.scorePassages(anyString(), anyString()))
        .thenReturn(new RerankingScores(List.of(1.5, -0.3, 0.7)));

    List<LLMReranker.ScoredChunk> results = reranker.rerank(query, candidates, 3);

    assertThat(results.get(0).score()).isLessThanOrEqualTo(1.0);
    assertThat(results.get(0).score()).isGreaterThanOrEqualTo(0.0);
    assertThat(results.get(1).score()).isLessThanOrEqualTo(1.0);
    assertThat(results.get(1).score()).isGreaterThanOrEqualTo(0.0);
  }

  @Test
  @DisplayName("Should handle LLM exception with fallback scores")
  void shouldHandleLlmExceptionWithFallbackScores() {
    List<DocumentChunk> candidates = createChunks(3);
    candidates.get(0).setRelevanceScore(0.03);
    candidates.get(1).setRelevanceScore(0.02);
    candidates.get(2).setRelevanceScore(0.01);

    String query = "test query";

    when(agent.scorePassages(anyString(), anyString()))
        .thenThrow(new RuntimeException("LLM API error"));

    List<LLMReranker.ScoredChunk> results = reranker.rerank(query, candidates, 3);

    assertThat(results).hasSize(3);
    assertThat(results.get(0).score()).isGreaterThan(0.0);
  }

  @Test
  @DisplayName("Should skip reranking when disabled")
  void shouldSkipRerankingWhenDisabled() {
    ReflectionTestUtils.setField(reranker, "enabled", false);

    List<DocumentChunk> candidates = createChunks(3);
    candidates.get(0).setRelevanceScore(0.9);
    candidates.get(1).setRelevanceScore(0.7);
    candidates.get(2).setRelevanceScore(0.5);

    String query = "test query";

    List<LLMReranker.ScoredChunk> results = reranker.rerank(query, candidates, 3);

    assertThat(results).hasSize(3);
    assertThat(results.get(0).score()).isEqualTo(0.9);
    assertThat(results.get(1).score()).isEqualTo(0.7);
    assertThat(results.get(2).score()).isEqualTo(0.5);
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
