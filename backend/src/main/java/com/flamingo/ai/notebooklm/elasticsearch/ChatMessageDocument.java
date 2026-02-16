package com.flamingo.ai.notebooklm.elasticsearch;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a chat message stored in Elasticsearch with text and vector embedding.
 *
 * <p>Used for semantic search over chat history to find relevant past conversations for query
 * reformulation and context enrichment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDocument {

  /** Message ID (from ChatMessage entity). */
  private String id;

  /** Session ID this message belongs to. */
  private UUID sessionId;

  /** Message role (USER or ASSISTANT). */
  private String role;

  /** Message content (text). */
  private String content;

  /** Vector embedding of the content for semantic search. */
  private List<Float> embedding;

  /** Message timestamp (epoch milliseconds). */
  private Long timestamp;

  /** Token count of the message. */
  private Integer tokenCount;

  /** Relevance score from search results (set by search methods). */
  @Builder.Default private double relevanceScore = 0.0;
}
