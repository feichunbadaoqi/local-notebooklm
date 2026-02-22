package com.flamingo.ai.notebooklm.agent;

import com.flamingo.ai.notebooklm.agent.dto.DocumentAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI agent for combined document analysis: summary generation and topic extraction in a single LLM
 * call.
 *
 * <p>Returns structured JSON with both a rich summary and a list of key topics/concepts covered in
 * the document.
 */
public interface DocumentAnalysisAgent {

  @SystemMessage(
      """
        You are a document analysis expert. Analyze the provided document and return a JSON object
        with two fields:

        1. "summary": An 800-1000 word comprehensive summary in paragraph form. Capture the main
           topics, key arguments, methodology, findings, and essential information. Write in
           flowing prose. Do not use markdown headers or bullet points. Do not start with
           "This document" or "The document".

        2. "topics": An array of 5-15 key topics and concepts covered in the document. Each topic
           should be a descriptive phrase of 20-40 words that conveys what the document actually
           covers about that topic — not just a keyword. These topics will be used to ground
           follow-up suggestions, so they must accurately reflect the document's content.

        Return ONLY valid JSON matching this structure:
        {"summary": "...", "topics": ["...", "..."]}
        """)
  @UserMessage("""
        Document: {{fileName}}

        Content:
        {{content}}
        """)
  DocumentAnalysisResult analyze(@V("fileName") String fileName, @V("content") String content);
}
