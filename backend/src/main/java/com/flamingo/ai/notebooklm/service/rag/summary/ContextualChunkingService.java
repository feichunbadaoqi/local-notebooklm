package com.flamingo.ai.notebooklm.service.rag.summary;

import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import java.util.List;

/**
 * Service for generating contextual prefixes for document chunks.
 *
 * <p>Enriches each chunk with a 1-2 sentence prefix describing how it fits within the broader
 * document, improving retrieval quality for both BM25 and vector search.
 */
public interface ContextualChunkingService {

  /**
   * Generates contextual prefixes for the given chunks and populates their {@code contextPrefix}
   * and {@code enrichedContent} fields.
   *
   * @param chunks the document chunks to enrich (modified in place)
   * @param documentSummary the document-level summary providing context
   * @param textsToEmbed the embedding texts list (updated with prefixed content)
   */
  void generatePrefixes(
      List<DocumentChunk> chunks, String documentSummary, List<String> textsToEmbed);
}
