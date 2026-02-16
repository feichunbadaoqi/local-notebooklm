package com.flamingo.ai.notebooklm.elasticsearch;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Elasticsearch document model for memories.
 *
 * <p>Represents extracted memories indexed in Elasticsearch for semantic search. Used to find
 * relevant memories based on query similarity combined with importance scoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryDocument {

  /** Unique identifier (matches Memory.id) */
  private String id;

  /** Session this memory belongs to */
  private UUID sessionId;

  /** Memory content text */
  private String memoryContent;

  /** Memory type (FACT, PREFERENCE, CONTEXT, RELATIONSHIP) */
  private String memoryType;

  /** Importance score (0.0 to 1.0) for hybrid scoring */
  private Float importance;

  /** Vector embedding for semantic search */
  private List<Float> embedding;

  /** Timestamp when memory was created */
  private Long timestamp;

  /** Relevance score from search (set by hybrid search service) */
  @Builder.Default private double relevanceScore = 0.0;
}
