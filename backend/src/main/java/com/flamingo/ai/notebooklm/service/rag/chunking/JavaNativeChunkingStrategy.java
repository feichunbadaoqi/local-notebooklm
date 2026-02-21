package com.flamingo.ai.notebooklm.service.rag.chunking;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.service.rag.model.ChunkingResult;
import com.flamingo.ai.notebooklm.service.rag.model.DocumentContext;
import com.flamingo.ai.notebooklm.service.rag.model.ParsedDocument;
import com.flamingo.ai.notebooklm.service.rag.model.RawDocumentChunk;
import com.flamingo.ai.notebooklm.service.rag.parsing.CommonmarkDocumentParser;
import com.flamingo.ai.notebooklm.service.rag.parsing.DocumentParser;
import com.flamingo.ai.notebooklm.service.rag.parsing.PdfBoxDocumentParser;
import com.flamingo.ai.notebooklm.service.rag.parsing.TikaXhtmlDocumentParser;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * {@link DocumentChunkingStrategy} that uses Java-native libraries for structured parsing.
 *
 * <p>Routes to the appropriate {@link DocumentParser} implementation based on MIME type:
 *
 * <ul>
 *   <li>{@code application/pdf} → {@link PdfBoxDocumentParser} (PDFBox 3.x)
 *   <li>{@code application/vnd.openxml*}, {@code application/msword}, {@code text/plain} → {@link
 *       TikaXhtmlDocumentParser} (Apache Tika XHTML output)
 *   <li>{@code text/markdown}, {@code text/x-markdown} → {@link CommonmarkDocumentParser}
 * </ul>
 *
 * <p>All formats share the same {@link SectionAwareChunker} which produces semantically coherent
 * chunks aligned to section boundaries.
 *
 * <p>Registered at {@code @Order(10)} — lower than any future Docling strategy ({@code @Order(1)})
 * so Docling would take priority when available.
 */
@Service
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class JavaNativeChunkingStrategy implements DocumentChunkingStrategy {

  private final PdfBoxDocumentParser pdfBoxParser;
  private final TikaXhtmlDocumentParser tikaParser;
  private final CommonmarkDocumentParser commonmarkParser;
  private final SectionAwareChunker chunker;
  private final RagConfig ragConfig;

  @Override
  public ChunkingResult chunkDocument(InputStream inputStream, DocumentContext context) {
    String mimeType = context.mimeType();
    DocumentParser parser = selectParser(mimeType);

    log.debug(
        "JavaNativeChunkingStrategy: mimeType={}, parser={}",
        mimeType,
        parser.getClass().getSimpleName());

    ParsedDocument parsed = parser.parse(inputStream, mimeType);
    List<RawDocumentChunk> chunks = chunker.chunk(parsed, ragConfig);

    log.debug(
        "Parsed document for {}: {} sections, {} tables, {} images, {} chunks",
        context.fileName(),
        parsed.sections().size(),
        parsed.tables().size(),
        parsed.images().size(),
        chunks.size());

    return new ChunkingResult(chunks, parsed.images(), parsed.fullText());
  }

  @Override
  public boolean supports(String mimeType) {
    return pdfBoxParser.supports(mimeType)
        || tikaParser.supports(mimeType)
        || commonmarkParser.supports(mimeType);
  }

  private DocumentParser selectParser(String mimeType) {
    if (pdfBoxParser.supports(mimeType)) {
      return pdfBoxParser;
    }
    if (commonmarkParser.supports(mimeType)) {
      return commonmarkParser;
    }
    // DOCX, PPTX, XLSX, plain text, and everything else Tika can handle
    return tikaParser;
  }
}
