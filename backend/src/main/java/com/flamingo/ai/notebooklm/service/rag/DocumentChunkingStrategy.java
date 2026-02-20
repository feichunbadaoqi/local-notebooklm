package com.flamingo.ai.notebooklm.service.rag;

import java.io.InputStream;

/**
 * High-level strategy for turning a raw document stream into a {@link ChunkingResult}.
 *
 * <p>A strategy encapsulates one full parsing-and-chunking pipeline (e.g. Java-native PDFBox +
 * section-aware chunker, or a future Docling REST-API integration). {@link
 * com.flamingo.ai.notebooklm.service.rag.DocumentProcessingService} depends exclusively on this
 * interface — never on concrete implementations.
 *
 * <p><strong>Open/Closed principle:</strong> to adopt a new parsing technology (e.g. Docling),
 * create a new {@code DocumentChunkingStrategy} implementation and register it as a Spring bean
 * with a higher {@code @Order}. Zero changes to any existing class are required.
 */
public interface DocumentChunkingStrategy {

  /**
   * Parses and chunks the document.
   *
   * <p>The caller retains ownership of {@code inputStream}; implementations must not close it.
   *
   * @param inputStream raw document bytes
   * @param context document identity metadata
   * @return chunks, extracted images, and full plain text
   */
  ChunkingResult chunkDocument(InputStream inputStream, DocumentContext context);

  /**
   * Returns {@code true} if this strategy can handle the given MIME type.
   *
   * @param mimeType document MIME type
   * @return {@code true} if supported
   */
  boolean supports(String mimeType);
}
