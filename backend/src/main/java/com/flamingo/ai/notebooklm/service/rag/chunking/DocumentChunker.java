package com.flamingo.ai.notebooklm.service.rag.chunking;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.service.rag.model.ParsedDocument;
import com.flamingo.ai.notebooklm.service.rag.model.RawDocumentChunk;
import java.util.List;

/**
 * Splits a {@link ParsedDocument} into a list of {@link RawDocumentChunk}s ready for embedding.
 *
 * <p>Implementations may use different strategies (section-aware, sliding-window, …). They must be
 * stateless and safe for concurrent use.
 *
 * <p>Follows the <em>Single Responsibility Principle</em>: a chunker only chunks — it does not
 * parse or embed.
 */
public interface DocumentChunker {

  /**
   * Produces chunks from the structured document representation.
   *
   * @param document the parsed document
   * @param config RAG configuration (chunk size, overlap, etc.)
   * @return ordered list of chunks
   */
  List<RawDocumentChunk> chunk(ParsedDocument document, RagConfig config);
}
