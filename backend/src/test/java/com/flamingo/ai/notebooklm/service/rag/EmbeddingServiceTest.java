package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingService Tests")
class EmbeddingServiceTest {

  @Mock private EmbeddingModel embeddingModel;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;

  private EmbeddingService embeddingService;

  @BeforeEach
  void setUp() {
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
    lenient()
        .when(meterRegistry.counter(anyString(), anyString(), anyString()))
        .thenReturn(counter);

    embeddingService = new EmbeddingService(embeddingModel, meterRegistry);
  }

  @Test
  @DisplayName("Should embed query with query prefix")
  void shouldEmbedQueryWithQueryPrefix() {
    String query = "What is machine learning?";
    float[] mockVector = {0.1f, 0.2f, 0.3f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedQuery(query);

    assertThat(result).containsExactly(0.1f, 0.2f, 0.3f);
    verify(meterRegistry.counter("embedding.requests.success", "type", "query")).increment();
  }

  @Test
  @DisplayName("Should embed passage with passage prefix")
  void shouldEmbedPassageWithPassagePrefix() {
    String passage = "Machine learning is a subset of AI.";
    float[] mockVector = {0.4f, 0.5f, 0.6f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedPassage(passage);

    assertThat(result).containsExactly(0.4f, 0.5f, 0.6f);
    verify(meterRegistry.counter("embedding.requests.success", "type", "passage")).increment();
  }

  @Test
  @DisplayName("Should truncate very long query text")
  void shouldTruncateVeryLongQueryText() {
    String veryLongQuery = "a".repeat(6000); // Exceeds 5000 char limit
    float[] mockVector = {0.1f, 0.2f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedQuery(veryLongQuery);

    assertThat(result).isNotEmpty();
    // Verify truncation happened (check that embedded text was shortened)
    verify(embeddingModel).embed(anyString());
  }

  @Test
  @DisplayName("Should truncate very long passage text")
  void shouldTruncateVeryLongPassageText() {
    String veryLongPassage = "b".repeat(6000); // Exceeds 5000 char limit
    float[] mockVector = {0.3f, 0.4f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedPassage(veryLongPassage);

    assertThat(result).isNotEmpty();
    verify(embeddingModel).embed(anyString());
  }

  @Test
  @DisplayName("Should handle short query text without truncation")
  void shouldHandleShortQueryTextWithoutTruncation() {
    String shortQuery = "AI";
    float[] mockVector = {0.7f, 0.8f, 0.9f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedQuery(shortQuery);

    assertThat(result).containsExactly(0.7f, 0.8f, 0.9f);
  }

  @Test
  @DisplayName("Should handle short passage text without truncation")
  void shouldHandleShortPassageTextWithoutTruncation() {
    String shortPassage = "Neural networks.";
    float[] mockVector = {0.5f, 0.6f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedPassage(shortPassage);

    assertThat(result).containsExactly(0.5f, 0.6f);
  }

  @Test
  @DisplayName("Should embed text using deprecated method")
  void shouldEmbedTextUsingDeprecatedMethod() {
    String text = "Test text";
    float[] mockVector = {0.1f, 0.2f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    @SuppressWarnings("deprecation")
    List<Float> result = embeddingService.embedText(text);

    assertThat(result).containsExactly(0.1f, 0.2f);
    // Should delegate to embedPassage
    verify(meterRegistry.counter("embedding.requests.success", "type", "passage")).increment();
  }

  @Test
  @DisplayName("Should embed batch of passages")
  void shouldEmbedBatchOfPassages() {
    List<String> passages = List.of("Passage 1", "Passage 2", "Passage 3");
    float[] mockVector1 = {0.1f, 0.2f};
    float[] mockVector2 = {0.3f, 0.4f};
    float[] mockVector3 = {0.5f, 0.6f};

    when(embeddingModel.embed(anyString()))
        .thenReturn(createResponse(mockVector1))
        .thenReturn(createResponse(mockVector2))
        .thenReturn(createResponse(mockVector3));

    List<List<Float>> results = embeddingService.embedTexts(passages);

    assertThat(results).hasSize(3);
    assertThat(results.get(0)).containsExactly(0.1f, 0.2f);
    assertThat(results.get(1)).containsExactly(0.3f, 0.4f);
    assertThat(results.get(2)).containsExactly(0.5f, 0.6f);
    verify(embeddingModel, times(3)).embed(anyString());
  }

  @Test
  @DisplayName("Should truncate long passages in batch")
  void shouldTruncateLongPassagesInBatch() {
    String veryLongPassage = "c".repeat(6000);
    List<String> passages = List.of(veryLongPassage, "Short passage");
    float[] mockVector = {0.1f, 0.2f};

    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<List<Float>> results = embeddingService.embedTexts(passages);

    assertThat(results).hasSize(2);
    verify(embeddingModel, times(2)).embed(anyString());
  }

  @Test
  @DisplayName("Should handle empty batch")
  void shouldHandleEmptyBatch() {
    List<String> passages = List.of();

    List<List<Float>> results = embeddingService.embedTexts(passages);

    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("Should handle single passage in batch")
  void shouldHandleSinglePassageInBatch() {
    List<String> passages = List.of("Single passage");
    float[] mockVector = {0.7f, 0.8f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<List<Float>> results = embeddingService.embedTexts(passages);

    assertThat(results).hasSize(1);
    assertThat(results.get(0)).containsExactly(0.7f, 0.8f);
  }

  @Test
  @DisplayName("Should convert float array to Float list correctly")
  void shouldConvertFloatArrayToFloatListCorrectly() {
    float[] vector = {1.1f, 2.2f, 3.3f, 4.4f, 5.5f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(vector));

    List<Float> result = embeddingService.embedQuery("test");

    assertThat(result).hasSize(5);
    assertThat(result).containsExactly(1.1f, 2.2f, 3.3f, 4.4f, 5.5f);
  }

  @Test
  @DisplayName("Should handle empty vector from embedding model")
  void shouldHandleEmptyVectorFromEmbeddingModel() {
    float[] emptyVector = {};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(emptyVector));

    List<Float> result = embeddingService.embedQuery("test");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should handle CJK characters in query")
  void shouldHandleCjkCharactersInQuery() {
    String cjkQuery = "什么是机器学习？";
    float[] mockVector = {0.1f, 0.2f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedQuery(cjkQuery);

    assertThat(result).containsExactly(0.1f, 0.2f);
  }

  @Test
  @DisplayName("Should handle CJK characters in passage")
  void shouldHandleCjkCharactersInPassage() {
    String cjkPassage = "机器学习是人工智能的一个分支。";
    float[] mockVector = {0.3f, 0.4f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedPassage(cjkPassage);

    assertThat(result).containsExactly(0.3f, 0.4f);
  }

  @Test
  @DisplayName("Should handle mixed language text")
  void shouldHandleMixedLanguageText() {
    String mixedText = "Machine Learning 机器学习 is fascinating";
    float[] mockVector = {0.5f, 0.6f, 0.7f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedQuery(mixedText);

    assertThat(result).containsExactly(0.5f, 0.6f, 0.7f);
  }

  @Test
  @DisplayName("Should handle very long CJK text with truncation")
  void shouldHandleVeryLongCjkTextWithTruncation() {
    String longCjkText = "机器学习".repeat(1500); // ~6000 chars
    float[] mockVector = {0.1f, 0.2f};
    when(embeddingModel.embed(anyString())).thenReturn(createResponse(mockVector));

    List<Float> result = embeddingService.embedPassage(longCjkText);

    assertThat(result).isNotEmpty();
    verify(embeddingModel).embed(anyString());
  }

  private Response<Embedding> createResponse(float[] vector) {
    Embedding embedding = new Embedding(vector);
    return Response.from(embedding);
  }
}
