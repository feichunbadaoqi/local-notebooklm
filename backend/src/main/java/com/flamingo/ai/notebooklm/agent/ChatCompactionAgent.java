package com.flamingo.ai.notebooklm.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI agent for compacting chat history by generating concise summaries.
 *
 * <p>Preserves key facts, decisions, and context while reducing token usage in conversation
 * history.
 */
public interface ChatCompactionAgent {

  @SystemMessage(
      """
        You are a conversation summarization expert. Your task is to create concise,
        accurate summaries of conversation exchanges that preserve essential information.

        Guidelines:
        - Preserve all key facts, decisions, and important context
        - Maintain chronological order of events
        - Keep user preferences and stated requirements
        - Include specific data points (dates, numbers, names)
        - Remove redundant pleasantries and acknowledgments
        - Use clear, objective language
        - Be concise but complete

        The summary will be used to provide context for future conversations.
        """)
  @UserMessage(
      """
        Summarize the following conversation concisely, preserving key facts, decisions,
        and context:

        {{conversation}}
        """)
  String summarizeConversation(@V("conversation") String conversation);
}
