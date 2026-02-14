package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import java.util.UUID;

/**
 * Service for reformulating user queries by incorporating conversation context. Improves RAG search
 * quality in multi-turn conversations.
 */
public interface QueryReformulationService {
  /**
   * Reformulates a user query by incorporating conversation context if needed.
   *
   * @param sessionId The session ID to retrieve conversation history
   * @param originalQuery The raw user query
   * @param mode Current interaction mode (for logging/metrics)
   * @return Reformulated query (or original if standalone)
   */
  String reformulate(UUID sessionId, String originalQuery, InteractionMode mode);
}
