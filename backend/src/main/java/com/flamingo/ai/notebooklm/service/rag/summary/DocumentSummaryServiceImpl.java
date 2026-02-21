package com.flamingo.ai.notebooklm.service.rag.summary;

import com.flamingo.ai.notebooklm.agent.DocumentSummaryAgent;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Implementation of {@link DocumentSummaryService} using an LLM agent. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentSummaryServiceImpl implements DocumentSummaryService {

  private static final int MAX_INPUT_CHARS = 12_000;

  private final DocumentSummaryAgent documentSummaryAgent;

  @Override
  @Timed(value = "document.summary", description = "Time to generate document summary")
  public String generateSummary(String fileName, String fullText) {
    if (fullText == null || fullText.isBlank()) {
      log.warn("Cannot generate summary for '{}': empty content", fileName);
      return "";
    }

    String truncated =
        fullText.length() > MAX_INPUT_CHARS ? fullText.substring(0, MAX_INPUT_CHARS) : fullText;

    try {
      log.debug(
          "Generating summary for '{}' (input {} chars, truncated to {})",
          fileName,
          fullText.length(),
          truncated.length());
      String summary = documentSummaryAgent.summarize(fileName, truncated);
      log.debug("Generated summary for '{}': {} chars", fileName, summary.length());
      return summary;
    } catch (Exception e) {
      log.error("Failed to generate summary for '{}': {}", fileName, e.getMessage());
      return "";
    }
  }
}
