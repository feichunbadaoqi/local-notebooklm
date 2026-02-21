package com.flamingo.ai.notebooklm.service.rag.parsing;

import com.flamingo.ai.notebooklm.service.rag.model.ParsedDocument;
import java.io.InputStream;

/**
 * Parses a raw document byte-stream into a structured {@link ParsedDocument}.
 *
 * <p>Implementations are format-specific (PDF, DOCX, Markdown, …). They must be stateless so a
 * single instance can be shared across concurrent document-processing threads.
 *
 * <p>Follows the <em>Single Responsibility Principle</em>: a parser only parses — it does not chunk
 * or embed.
 */
public interface DocumentParser {

  /**
   * Parses the given document stream.
   *
   * <p>The caller retains ownership of {@code inputStream}; implementations must not close it.
   *
   * @param inputStream raw document bytes
   * @param mimeType MIME type of the document (e.g. {@code application/pdf})
   * @return structured representation of the document
   */
  ParsedDocument parse(InputStream inputStream, String mimeType);

  /**
   * Returns {@code true} if this parser can handle the given MIME type.
   *
   * @param mimeType document MIME type
   * @return {@code true} if supported
   */
  boolean supports(String mimeType);
}
