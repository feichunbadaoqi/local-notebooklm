package com.flamingo.ai.notebooklm.service.rag.summary;

import com.flamingo.ai.notebooklm.agent.DocumentAnalysisAgent;
import com.flamingo.ai.notebooklm.agent.DocumentSummaryAgent;
import com.flamingo.ai.notebooklm.agent.dto.DocumentAnalysisResult;
import io.micrometer.core.annotation.Timed;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Implementation of {@link DocumentSummaryService} using LLM agents. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSummaryServiceImpl implements DocumentSummaryService {

  private static final int MAX_INPUT_CHARS = 12_000;

  private final DocumentSummaryAgent documentSummaryAgent;
  private final DocumentAnalysisAgent documentAnalysisAgent;

  @Override
  @Timed(value = "document.summary", description = "Time to generate document summary")
  public String generateSummary(String fileName, String fullText) {
    return analyzeDocument(fileName, fullText).summary();
  }

  @Override
  @Timed(value = "document.analysis", description = "Time to analyze document (summary + topics)")
  public DocumentAnalysisResult analyzeDocument(String fileName, String fullText) {
    if (fullText == null || fullText.isBlank()) {
      log.warn("Cannot analyze '{}': empty content", fileName);
      return new DocumentAnalysisResult("", List.of());
    }

    String truncated =
        fullText.length() > MAX_INPUT_CHARS ? fullText.substring(0, MAX_INPUT_CHARS) : fullText;

    try {
      log.debug(
          "Analyzing document '{}' (input {} chars, truncated to {})",
          fileName,
          fullText.length(),
          truncated.length());
      DocumentAnalysisResult result = documentAnalysisAgent.analyze(fileName, truncated);
      log.debug(
          "Analysis complete for '{}': {} char summary, {} topics",
          fileName,
          result.summary() != null ? result.summary().length() : 0,
          result.topics() != null ? result.topics().size() : 0);
      return new DocumentAnalysisResult(
          result.summary() != null ? result.summary() : "",
          result.topics() != null ? result.topics() : List.of());
    } catch (Exception e) {
      log.warn(
          "DocumentAnalysisAgent failed for '{}', falling back to summary-only: {}",
          fileName,
          e.getMessage());
      return fallbackToSummaryOnly(fileName, truncated);
    }
  }

  private DocumentAnalysisResult fallbackToSummaryOnly(String fileName, String truncatedText) {
    try {
      String summary = documentSummaryAgent.summarize(fileName, truncatedText);
      log.debug("Fallback summary generated for '{}': {} chars", fileName, summary.length());
      return new DocumentAnalysisResult(summary, List.of());
    } catch (Exception e) {
      log.error("Fallback summary also failed for '{}': {}", fileName, e.getMessage());
      return new DocumentAnalysisResult("", List.of());
    }
  }
}
