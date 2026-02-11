package com.flamingo.ai.notebooklm.service.chat;

import com.flamingo.ai.notebooklm.api.dto.response.StreamChunkResponse;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Flux;

/** Service for handling chat interactions with RAG-enhanced responses. */
public interface ChatService {

  /**
   * Streams a chat response for the given message.
   *
   * @param sessionId the session ID
   * @param userMessage the user's message
   * @return a Flux of stream chunk responses
   */
  Flux<StreamChunkResponse> streamChat(UUID sessionId, String userMessage);

  /**
   * Gets chat history for a session.
   *
   * @param sessionId the session ID
   * @param limit maximum number of messages to return
   * @return list of chat messages
   */
  List<ChatMessage> getChatHistory(UUID sessionId, int limit);

  /**
   * Gets non-compacted chat history for context building.
   *
   * @param sessionId the session ID
   * @return list of recent non-compacted messages
   */
  List<ChatMessage> getRecentMessages(UUID sessionId);
}
