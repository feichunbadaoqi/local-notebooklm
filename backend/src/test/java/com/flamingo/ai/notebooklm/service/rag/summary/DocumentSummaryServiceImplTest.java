package com.flamingo.ai.notebooklm.service.rag.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.agent.DocumentAnalysisAgent;
import com.flamingo.ai.notebooklm.agent.DocumentSummaryAgent;
import com.flamingo.ai.notebooklm.agent.dto.DocumentAnalysisResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentSummaryServiceImplTest {

  @Mock private DocumentSummaryAgent documentSummaryAgent;
  @Mock private DocumentAnalysisAgent documentAnalysisAgent;

  @InjectMocks private DocumentSummaryServiceImpl service;

  @Test
  @DisplayName("should generate summary via analyzeDocument for valid content")
  void shouldGenerateSummary_whenValidContent() {
    when(documentAnalysisAgent.analyze(anyString(), anyString()))
        .thenReturn(new DocumentAnalysisResult("A concise summary of the document.", List.of()));

    String result = service.generateSummary("report.pdf", "Some document content here.");

    assertThat(result).isEqualTo("A concise summary of the document.");
    verify(documentAnalysisAgent).analyze("report.pdf", "Some document content here.");
  }

  @Test
  @DisplayName("should return empty string for blank input")
  void shouldReturnEmpty_whenBlankInput() {
    String result = service.generateSummary("empty.pdf", "   ");

    assertThat(result).isEmpty();
    verify(documentAnalysisAgent, never()).analyze(anyString(), anyString());
  }

  @Test
  @DisplayName("should return empty string for null input")
  void shouldReturnEmpty_whenNullInput() {
    String result = service.generateSummary("null.pdf", null);

    assertThat(result).isEmpty();
    verify(documentAnalysisAgent, never()).analyze(anyString(), anyString());
  }

  @Test
  @DisplayName("should truncate input to 12000 chars")
  void shouldTruncateInput_whenExceedsMaxChars() {
    String longContent = "a".repeat(20_000);
    when(documentAnalysisAgent.analyze(anyString(), anyString()))
        .thenReturn(new DocumentAnalysisResult("Summary.", List.of()));

    service.generateSummary("big.pdf", longContent);

    ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
    verify(documentAnalysisAgent).analyze(anyString(), contentCaptor.capture());
    assertThat(contentCaptor.getValue()).hasSize(12_000);
  }

  @Test
  @DisplayName("should fall back to summary-only agent on analysis failure")
  void shouldFallback_whenAnalysisAgentFails() {
    when(documentAnalysisAgent.analyze(anyString(), anyString()))
        .thenThrow(new RuntimeException("LLM error"));
    when(documentSummaryAgent.summarize(anyString(), anyString())).thenReturn("Fallback summary.");

    String result = service.generateSummary("fail.pdf", "content");

    assertThat(result).isEqualTo("Fallback summary.");
    verify(documentSummaryAgent).summarize("fail.pdf", "content");
  }

  @Test
  @DisplayName("should return empty string when both agents fail")
  void shouldReturnEmpty_whenBothAgentsFail() {
    when(documentAnalysisAgent.analyze(anyString(), anyString()))
        .thenThrow(new RuntimeException("Analysis error"));
    when(documentSummaryAgent.summarize(anyString(), anyString()))
        .thenThrow(new RuntimeException("Summary error"));

    String result = service.generateSummary("fail.pdf", "content");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("should return analysis result with summary and topics")
  void shouldReturnAnalysisResult_whenAnalyzeDocument() {
    List<String> topics = List.of("Topic A description", "Topic B description");
    when(documentAnalysisAgent.analyze(anyString(), anyString()))
        .thenReturn(new DocumentAnalysisResult("Rich summary.", topics));

    DocumentAnalysisResult result = service.analyzeDocument("doc.pdf", "content");

    assertThat(result.summary()).isEqualTo("Rich summary.");
    assertThat(result.topics()).hasSize(2);
    assertThat(result.topics()).containsExactly("Topic A description", "Topic B description");
  }

  @Test
  @DisplayName("should handle null fields in analysis result")
  void shouldHandleNullFields_whenAnalyzeDocumentReturnsNulls() {
    when(documentAnalysisAgent.analyze(anyString(), anyString()))
        .thenReturn(new DocumentAnalysisResult(null, null));

    DocumentAnalysisResult result = service.analyzeDocument("doc.pdf", "content");

    assertThat(result.summary()).isEmpty();
    assertThat(result.topics()).isEmpty();
  }
}
