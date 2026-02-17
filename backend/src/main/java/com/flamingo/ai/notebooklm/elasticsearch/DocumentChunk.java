package com.flamingo.ai.notebooklm.elasticsearch;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a document chunk stored in Elasticsearch with both text and vector embedding.
 *
 * <p>Includes metadata fields for enhanced retrieval (RAG optimization): - documentTitle: Title
 * extracted from document or filename - sectionTitle: Current section/chapter heading - keywords:
 * Important terms extracted via TF-IDF - enrichedContent: Metadata-prefixed content for better
 * embedding
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk implements AbstractElasticsearchIndexService.ScoredDocument {

  private String id;
  private UUID documentId;
  private UUID sessionId;
  private String fileName;
  private int chunkIndex;
  private String content;
  private List<Float> embedding; // Keep for backward compatibility

  // Multiple embeddings for improved retrieval (Stage 2.2)
  private List<Float> titleEmbedding; // Embedding of title + section
  private List<Float> contentEmbedding; // Embedding of full content

  private int tokenCount;

  // Metadata fields for enhanced retrieval (RAG optimization Phase 1)
  @Builder.Default private String documentTitle = "";
  private String sectionTitle;
  @Builder.Default private List<String> keywords = List.of();
  private String enrichedContent;

  // Relevance score from search results (set by search methods)
  @Builder.Default private Double relevanceScore = 0.0;
}
