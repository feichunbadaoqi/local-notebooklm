package com.flamingo.ai.notebooklm.service.rag.summary;

/**
 * Service for generating document summaries using an LLM.
 *
 * <p>Produces concise summaries from the first portion of a document's text content, suitable for
 * display in the sources panel.
 */
public interface DocumentSummaryService {

  /**
   * Generates a summary for the given document content.
   *
   * @param fileName the document filename (provides context for the LLM)
   * @param fullText the full extracted text of the document
   * @return a 150-300 word summary, or empty string on failure
   */
  String generateSummary(String fileName, String fullText);
}
