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

  /**
   * Gets all memories for a session ordered by importance.
   *
   * @param sessionId the session ID
   * @return list of all memories
   */
  List<Memory> getAllMemories(UUID sessionId);

  /**
   * Gets a specific memory by ID.
   *
   * @param memoryId the memory ID
   * @return the memory
   */
  Memory getMemory(UUID memoryId);

  /**
   * Deletes a specific memory.
   *
   * @param memoryId the memory ID
   */
  void deleteMemory(UUID memoryId);

  /**
   * Manually adds a memory to a session.
   *
   * @param sessionId the session ID
   * @param content the memory content
   * @param type the memory type (fact, preference, insight)
   * @param importance the importance score (0.0 to 1.0)
   * @return the created memory
   */
  Memory addMemory(UUID sessionId, String content, String type, Float importance);

  /**
   * Validates that a memory belongs to the specified session.
   *
   * @param memoryId the memory ID
   * @param sessionId the expected session ID
   * @throws com.flamingo.ai.notebooklm.exception.MemoryNotFoundException if memory not found
   * @throws com.flamingo.ai.notebooklm.exception.MemoryAccessDeniedException if memory doesn't
   *     belong to session
   */
  void validateMemoryOwnership(UUID memoryId, UUID sessionId);
}
