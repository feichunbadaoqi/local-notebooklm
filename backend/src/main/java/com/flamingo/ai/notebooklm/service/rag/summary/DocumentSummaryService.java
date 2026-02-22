package com.flamingo.ai.notebooklm.service.rag.summary;

import com.flamingo.ai.notebooklm.agent.dto.DocumentAnalysisResult;

/**
 * Service for generating document summaries and extracting topics using an LLM.
 *
 * <p>Produces summaries and topic lists from the first portion of a document's text content,
 * suitable for display in the sources panel and for grounding chat suggestions.
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

  /**
   * Analyzes a document to produce both a summary and a list of key topics in a single LLM call.
   *
   * @param fileName the document filename (provides context for the LLM)
   * @param fullText the full extracted text of the document
   * @return analysis result with summary and topics, or fallback with summary-only on failure
   */
  DocumentAnalysisResult analyzeDocument(String fileName, String fullText);
}
