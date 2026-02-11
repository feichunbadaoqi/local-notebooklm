package com.flamingo.ai.notebooklm.elasticsearch;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents a document chunk stored in Elasticsearch with both text and vector embedding. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

  private String id;
  private UUID documentId;
  private UUID sessionId;
  private String fileName;
  private int chunkIndex;
  private String content;
  private List<Float> embedding;
  private int tokenCount;
}
