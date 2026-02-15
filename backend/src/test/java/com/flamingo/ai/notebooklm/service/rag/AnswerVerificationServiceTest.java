package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import dev.langchain4j.model.chat.ChatModel;
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
@DisplayName("AnswerVerificationService Tests")
class AnswerVerificationServiceTest {

  @Mock private ChatModel chatModel;
  @Mock private MeterRegistry meterRegistry;
  @Mock private Counter counter;

  private AnswerVerificationService verificationService;

  @BeforeEach
  void setUp() {
    lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);

    verificationService = new AnswerVerificationService(chatModel, meterRegistry);
    ReflectionTestUtils.setField(verificationService, "supportThreshold", 0.7);
    ReflectionTestUtils.setField(verificationService, "enabled", true);
  }

  @Test
  @DisplayName("Should verify supported claims successfully")
  void shouldVerifySupportedClaimsSuccessfully() {
    String answer =
        "Machine learning is a subset of AI. [Source 1] It uses algorithms to learn from data."
            + " [Source 1]";
    List<DocumentChunk> evidence = createEvidence("Machine learning is indeed a subset of AI.");

    when(chatModel.chat(anyString())).thenReturn("0.9");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isTrue();
    assertThat(result.getUnsupportedClaims()).isEmpty();
  }

  @Test
  @DisplayName("Should detect unsupported claims")
  void shouldDetectUnsupportedClaims() {
    String answer =
        "Machine learning was invented in 1950. [Source 1]"; // Not supported by evidence
    List<DocumentChunk> evidence = createEvidence("Machine learning became popular in the 2000s.");

    when(chatModel.chat(anyString())).thenReturn("0.3"); // Low support score

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getUnsupportedClaims()).hasSize(1);
    assertThat(result.getUnsupportedClaims().get(0).getSupportScore()).isEqualTo(0.3);
  }

  @Test
  @DisplayName("Should extract citations in [Source N] format")
  void shouldExtractCitationsInSourceNFormat() {
    String answer =
        "Deep learning uses neural networks. [Source 1] CNNs are great for images. [Source 2]";
    List<DocumentChunk> evidence =
        createEvidence(
            "Deep learning uses multi-layer neural networks.",
            "Convolutional neural networks excel at image processing.");

    when(chatModel.chat(anyString())).thenReturn("0.9"); // High support

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    // Should extract both citations
    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should extract citations in [N] format")
  void shouldExtractCitationsInNFormat() {
    String answer = "Machine learning is powerful. [1] It learns from data. [2]";
    List<DocumentChunk> evidence =
        createEvidence("Machine learning is very powerful.", "ML systems learn from data.");

    when(chatModel.chat(anyString())).thenReturn("0.8");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should extract citations in (Source N) format")
  void shouldExtractCitationsInParenthesesFormat() {
    String answer = "Neural networks have layers. (Source 1) They process information. (Source 2)";
    List<DocumentChunk> evidence =
        createEvidence("Neural networks consist of layers.", "They process inputs sequentially.");

    when(chatModel.chat(anyString())).thenReturn("0.85");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should handle answer with no citations")
  void shouldHandleAnswerWithNoCitations() {
    String answer = "This is a general statement without any citations.";
    List<DocumentChunk> evidence = createEvidence("Some evidence");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    // No citations to verify, should be valid
    assertThat(result.isValid()).isTrue();
    assertThat(result.getUnsupportedClaims()).isEmpty();
  }

  @Test
  @DisplayName("Should handle empty answer")
  void shouldHandleEmptyAnswer() {
    String answer = "";
    List<DocumentChunk> evidence = createEvidence("Some evidence");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should handle null answer")
  void shouldHandleNullAnswer() {
    String answer = null;
    List<DocumentChunk> evidence = createEvidence("Some evidence");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should handle empty evidence list")
  void shouldHandleEmptyEvidenceList() {
    String answer = "Machine learning is powerful. [Source 1]";
    List<DocumentChunk> evidence = new ArrayList<>();

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should handle null evidence list")
  void shouldHandleNullEvidenceList() {
    String answer = "Machine learning is powerful. [Source 1]";
    List<DocumentChunk> evidence = null;

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should ignore citations with out-of-bounds index")
  void shouldIgnoreCitationsWithOutOfBoundsIndex() {
    String answer = "Machine learning is powerful. [Source 10]"; // Index out of bounds
    List<DocumentChunk> evidence = createEvidence("Only one piece of evidence");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    // Should ignore invalid citation, no claims to verify
    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should handle LLM returning invalid score format")
  void shouldHandleLlmReturningInvalidScoreFormat() {
    String answer = "Machine learning is AI. [Source 1]";
    List<DocumentChunk> evidence = createEvidence("Machine learning is part of AI.");

    when(chatModel.chat(anyString())).thenReturn("invalid");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    // Should default to 0.5, which is below threshold 0.7
    assertThat(result.isValid()).isFalse();
  }

  @Test
  @DisplayName("Should handle LLM exception gracefully")
  void shouldHandleLlmExceptionGracefully() {
    String answer = "Machine learning learns from data. [Source 1]";
    List<DocumentChunk> evidence = createEvidence("ML uses data to learn.");

    when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM API error"));

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    // Should default to 0.5, below threshold
    assertThat(result.isValid()).isFalse();
  }

  @Test
  @DisplayName("Should skip verification when disabled")
  void shouldSkipVerificationWhenDisabled() {
    ReflectionTestUtils.setField(verificationService, "enabled", false);

    String answer = "Completely unsupported claim. [Source 1]";
    List<DocumentChunk> evidence = createEvidence("Unrelated evidence");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    // Should skip verification and return valid
    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should use configurable support threshold")
  void shouldUseConfigurableSupportThreshold() {
    ReflectionTestUtils.setField(verificationService, "supportThreshold", 0.5);

    String answer = "Medium support claim. [Source 1]";
    List<DocumentChunk> evidence = createEvidence("Some evidence");

    when(chatModel.chat(anyString())).thenReturn("0.6"); // Above 0.5 threshold

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should handle multiple sentences with same citation")
  void shouldHandleMultipleSentencesWithSameCitation() {
    String answer =
        "Machine learning is powerful. Neural networks are used. Both use data. [Source 1]";
    List<DocumentChunk> evidence = createEvidence("ML and neural networks both use data.");

    when(chatModel.chat(anyString())).thenReturn("0.8");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  @DisplayName("Should truncate very long evidence for scoring")
  void shouldTruncateVeryLongEvidenceForScoring() {
    String answer = "This is a claim. [Source 1]";
    StringBuilder longEvidence = new StringBuilder();
    for (int i = 0; i < 2000; i++) {
      longEvidence.append("word ");
    }
    List<DocumentChunk> evidence = createEvidence(longEvidence.toString());

    when(chatModel.chat(anyString())).thenReturn("0.8");

    AnswerVerificationService.VerificationResult result =
        verificationService.verify(answer, evidence);

    // Should handle truncation without error
    assertThat(result).isNotNull();
  }

  private List<DocumentChunk> createEvidence(String... contents) {
    List<DocumentChunk> chunks = new ArrayList<>();
    for (String content : contents) {
      chunks.add(
          DocumentChunk.builder()
              .id(UUID.randomUUID().toString())
              .documentId(UUID.randomUUID())
              .sessionId(UUID.randomUUID())
              .fileName("test.pdf")
              .chunkIndex(0)
              .content(content)
              .tokenCount(10)
              .build());
    }
    return chunks;
  }
}
