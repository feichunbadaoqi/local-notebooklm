package com.flamingo.ai.notebooklm.service.rag.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.agent.DocumentSummaryAgent;
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

  @InjectMocks private DocumentSummaryServiceImpl service;

  @Test
  @DisplayName("should generate summary for valid content")
  void shouldGenerateSummary_whenValidContent() {
    when(documentSummaryAgent.summarize(anyString(), anyString()))
        .thenReturn("A concise summary of the document.");

    String result = service.generateSummary("report.pdf", "Some document content here.");

    assertThat(result).isEqualTo("A concise summary of the document.");
    verify(documentSummaryAgent).summarize("report.pdf", "Some document content here.");
  }

  @Test
  @DisplayName("should return empty string for blank input")
  void shouldReturnEmpty_whenBlankInput() {
    String result = service.generateSummary("empty.pdf", "   ");

    assertThat(result).isEmpty();
    verify(documentSummaryAgent, never()).summarize(anyString(), anyString());
  }

  @Test
  @DisplayName("should return empty string for null input")
  void shouldReturnEmpty_whenNullInput() {
    String result = service.generateSummary("null.pdf", null);

    assertThat(result).isEmpty();
    verify(documentSummaryAgent, never()).summarize(anyString(), anyString());
  }

  @Test
  @DisplayName("should truncate input to 12000 chars")
  void shouldTruncateInput_whenExceedsMaxChars() {
    String longContent = "a".repeat(20_000);
    when(documentSummaryAgent.summarize(anyString(), anyString())).thenReturn("Summary.");

    service.generateSummary("big.pdf", longContent);

    ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
    verify(documentSummaryAgent).summarize(anyString(), contentCaptor.capture());
    assertThat(contentCaptor.getValue()).hasSize(12_000);
  }

  @Test
  @DisplayName("should return empty string on agent failure")
  void shouldReturnEmpty_whenAgentFails() {
    when(documentSummaryAgent.summarize(anyString(), anyString()))
        .thenThrow(new RuntimeException("LLM error"));

    String result = service.generateSummary("fail.pdf", "content");

    assertThat(result).isEmpty();
  }
}
