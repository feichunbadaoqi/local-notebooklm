package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.config.RagConfig;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Fallback {@link DocumentChunkingStrategy} that uses Apache Tika's plain-text extraction.
 *
 * <p>Registered at {@code @Order(100)} — lowest priority, used only when no higher-priority
 * strategy (e.g. {@link JavaNativeChunkingStrategy}) reports {@code supports(mimeType) == true}.
 *
 * <p>This strategy always returns {@code supports(mimeType) == true} to act as a catch-all. It
 * delegates text extraction to Tika and applies a naive paragraph-based sliding-window chunker.
 * Section breadcrumbs are empty because plain-text extraction loses structure.
 */
@Service
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class TikaPlainTextChunkingStrategy implements DocumentChunkingStrategy {

  private static final int MAX_CHARS = 3500;
  private static final Tika TIKA = new Tika();

  private final RagConfig ragConfig;

  @Override
  public ChunkingResult chunkDocument(InputStream inputStream, DocumentContext context) {
    log.debug("TikaPlainTextChunkingStrategy fallback for mimeType={}", context.mimeType());

    String fullText;
    try {
      fullText = TIKA.parseToString(inputStream);
    } catch (Exception e) {
      log.error("Tika plain-text extraction failed: {}", e.getMessage());
      throw new com.flamingo.ai.notebooklm.exception.DocumentProcessingException(
          context.documentId(), "Failed to extract text: " + e.getMessage());
    }

    List<RawDocumentChunk> chunks = chunkText(fullText);
    return new ChunkingResult(chunks, List.of(), fullText);
  }

  @Override
  public boolean supports(String mimeType) {
    // Catch-all fallback
    return true;
  }

  private List<RawDocumentChunk> chunkText(String content) {
    int chunkSize = ragConfig.getChunking().getSize();
    int overlap = ragConfig.getChunking().getOverlap();
    List<String> rawChunks = slidingWindow(content, chunkSize, overlap);
    List<RawDocumentChunk> result = new ArrayList<>();
    for (int i = 0; i < rawChunks.size(); i++) {
      result.add(new RawDocumentChunk(rawChunks.get(i), List.of(), i, List.of()));
    }
    return result;
  }

  private List<String> slidingWindow(String content, int chunkSize, int overlap) {
    List<String> chunks = new ArrayList<>();
    String[] paragraphs = content.split("\\n\\n+");
    StringBuilder currentChunk = new StringBuilder();
    int currentTokens = 0;

    for (String paragraph : paragraphs) {
      if (paragraph.length() > MAX_CHARS) {
        if (currentChunk.length() > 0) {
          chunks.add(currentChunk.toString().trim());
          currentChunk = new StringBuilder();
          currentTokens = 0;
        }
        chunks.addAll(splitByChars(paragraph, MAX_CHARS));
        continue;
      }

      int paraTokens = paragraph.length() / 4;
      if (currentTokens + paraTokens > chunkSize && currentChunk.length() > 0) {
        chunks.add(currentChunk.toString().trim());
        String tail = overlapTail(currentChunk.toString(), overlap);
        currentChunk = new StringBuilder(tail);
        currentTokens = tail.length() / 4;
      }

      if (currentChunk.length() + paragraph.length() > MAX_CHARS && currentChunk.length() > 0) {
        chunks.add(currentChunk.toString().trim());
        String tail = overlapTail(currentChunk.toString(), overlap);
        currentChunk = new StringBuilder(tail);
        currentTokens = tail.length() / 4;
        if (currentChunk.length() + paragraph.length() > MAX_CHARS) {
          if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
            currentChunk = new StringBuilder();
            currentTokens = 0;
          }
          chunks.addAll(splitByChars(paragraph, MAX_CHARS));
          continue;
        }
      }

      currentChunk.append(paragraph).append("\n\n");
      currentTokens += paraTokens;
    }
    if (currentChunk.length() > 0) {
      chunks.add(currentChunk.toString().trim());
    }
    return chunks;
  }

  private List<String> splitByChars(String text, int maxChars) {
    List<String> chunks = new ArrayList<>();
    for (int i = 0; i < text.length(); i += maxChars - 100) {
      chunks.add(text.substring(i, Math.min(i + maxChars - 100, text.length())));
    }
    return chunks;
  }

  private String overlapTail(String text, int overlapTokens) {
    String[] words = text.split("\\s+");
    int keep = Math.min(overlapTokens, words.length);
    StringBuilder sb = new StringBuilder();
    for (int i = words.length - keep; i < words.length; i++) {
      sb.append(words[i]).append(" ");
    }
    return sb.toString();
  }
}
