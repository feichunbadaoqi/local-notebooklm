package com.flamingo.ai.notebooklm.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI agent for generating concise document summaries after upload.
 *
 * <p>Produces a 150-300 word plain-text summary capturing main topics, key arguments, and essential
 * information.
 */
public interface DocumentSummaryAgent {

  @SystemMessage(
      """
        You are a document summarization expert. Generate a concise 150-300 word summary
        of the provided document content. Capture the main topics, key arguments, and
        essential information. Write in paragraph form. Do not use markdown headers or
        bullet points. Do not start with "This document" or "The document".
        """)
  @UserMessage("""
        Document: {{fileName}}

        Content:
        {{content}}
        """)
  String summarize(@V("fileName") String fileName, @V("content") String content);
}
