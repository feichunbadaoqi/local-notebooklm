package com.flamingo.ai.notebooklm.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.TokenStream;
import java.util.List;

/**
 * AI agent for streaming chat responses.
 *
 * <p>Uses LangChain4j TokenStream for reactive streaming of LLM responses. The agent receives a
 * full conversation history (system messages, user messages, assistant messages) and streams the
 * response token by token.
 *
 * <p>Note: This agent does NOT use @SystemMessage/@UserMessage annotations because the messages are
 * dynamically built based on session state (interaction mode, RAG context, memories, summaries).
 * Instead, the service layer builds the full message list and passes it to this agent.
 */
public interface ChatStreamingAgent {

  /**
   * Streams a chat response given the full conversation context.
   *
   * @param messages the full conversation history (system + user + assistant messages)
   * @return TokenStream for streaming the response
   */
  TokenStream chat(List<ChatMessage> messages);
}
