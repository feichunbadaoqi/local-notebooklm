package com.flamingo.ai.notebooklm.service.rag.topic;

import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import java.util.UUID;

/**
 * Service for building a topic index from documents in a session.
 *
 * <p>The topic index is injected into the chat system prompt to constrain the LLM's follow-up
 * suggestions to topics actually covered in the user's documents.
 */
public interface TopicIndexService {

  /**
   * Builds a formatted topic index string for the given session and mode.
   *
   * @param sessionId the session whose documents to index
   * @param mode the current interaction mode (affects trailing instruction)
   * @return the formatted topic index, or empty string if no topics are available
   */
  String buildTopicIndex(UUID sessionId, InteractionMode mode);
}
