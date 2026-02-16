package com.flamingo.ai.notebooklm.service.memory;

import com.flamingo.ai.notebooklm.domain.entity.Memory;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import java.util.List;
import java.util.UUID;

/** Service for extracting and managing conversation memories. */
public interface MemoryService {

  /**
   * Extracts memories from a conversation exchange and saves them asynchronously. Called after each
   * chat response to capture important facts, preferences, and insights.
   *
   * @param sessionId the session ID
   * @param userMessage the user's message
   * @param assistantResponse the assistant's response
   * @param mode the interaction mode (affects extraction style)
   */
  void extractAndSaveAsync(
      UUID sessionId, String userMessage, String assistantResponse, InteractionMode mode);

  /**
   * Gets relevant memories for the current query. Used to enrich the conversation context with
   * long-term knowledge.
   *
   * @param sessionId the session ID
   * @param query the current user query
   * @param limit maximum number of memories to return
   * @return list of relevant memories ordered by relevance
   */
  List<Memory> getRelevantMemories(UUID sessionId, String query, int limit);

  /**
   * Builds a formatted string of memories for inclusion in the system prompt.
   *
   * @param memories the memories to format
   * @return formatted memory context string
   */
  String buildMemoryContext(List<Memory> memories);
}
