package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.config.RagConfig;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link DocumentChunker} that produces chunks aligned to section boundaries.
 *
 * <p>The algorithm walks the {@link DocumentSection} tree depth-first. If a section's content fits
 * within the configured chunk size, it is emitted as a single chunk (preserving the full
 * breadcrumb). Sections that exceed the limit are split at paragraph boundaries ({@code \n\n}),
 * then at sentence boundaries, then by character count — preserving the sliding-window overlap
 * identical to the legacy chunking logic.
 *
 * <p>Markdown tables embedded in section content are never split mid-table: each table is kept
 * whole in the chunk it first appears in.
 *
 * <p>If the parsed document has no detected sections (empty list), the chunker falls back to
 * applying the sliding-window algorithm to {@link ParsedDocument#fullText()} directly, producing
 * chunks with an empty breadcrumb.
 */
@Service
@Slf4j
public class SectionAwareChunker implements DocumentChunker {

  /** Maximum characters per chunk before splitting (conservative embedding-limit guard). */
  private static final int MAX_CHARS = 3500;

  @Override
  public List<RawDocumentChunk> chunk(ParsedDocument document, RagConfig config) {
    int chunkSize = config.getChunking().getSize();
    int overlap = config.getChunking().getOverlap();

    List<RawDocumentChunk> result = new ArrayList<>();

    if (document.sections().isEmpty()) {
      // Fallback: treat full text as a single implicit section
      List<String> slidingChunks = slidingWindow(document.fullText(), chunkSize, overlap);
      for (int i = 0; i < slidingChunks.size(); i++) {
        result.add(new RawDocumentChunk(slidingChunks.get(i), List.of(), i, List.of()));
      }
    } else {
      walkSections(document.sections(), document, chunkSize, overlap, result);
    }

    // Associate images with chunks by approximate offset
    associateImages(result, document);

    log.debug("SectionAwareChunker produced {} chunks", result.size());
    return result;
  }

  // ---- recursive section walk ----

  private void walkSections(
      List<DocumentSection> sections,
      ParsedDocument document,
      int chunkSize,
      int overlap,
      List<RawDocumentChunk> result) {

    for (DocumentSection section : sections) {
      String content = section.content().trim();
      List<String> breadcrumb = section.breadcrumb();

      if (!content.isBlank()) {
        List<String> sectionChunks = chunkSectionContent(content, chunkSize, overlap);
        for (String chunk : sectionChunks) {
          int idx = result.size();
          result.add(new RawDocumentChunk(chunk, breadcrumb, idx, new ArrayList<>()));
        }
      }

      // Recurse into children
      if (!section.children().isEmpty()) {
        walkSections(section.children(), document, chunkSize, overlap, result);
      }
    }
  }

  private List<String> chunkSectionContent(String content, int chunkSize, int overlap) {
    if (content.length() <= MAX_CHARS && estimateTokens(content) <= chunkSize) {
      return List.of(content);
    }

    // Preserve whole Markdown tables by splitting around them first
    List<String> segments = splitAroundTables(content);
    List<String> chunks = new ArrayList<>();

    for (String segment : segments) {
      if (segment.startsWith("|")) {
        // This segment is a Markdown table — always keep it whole (or truncate if huge)
        if (segment.length() <= MAX_CHARS) {
          chunks.add(segment);
        } else {
          chunks.add(segment.substring(0, MAX_CHARS));
        }
      } else {
        chunks.addAll(slidingWindow(segment, chunkSize, overlap));
      }
    }
    return chunks;
  }

  /**
   * Splits content into alternating non-table and table segments.
   *
   * <p>A "table segment" is a run of lines that each start with {@code |}.
   */
  private List<String> splitAroundTables(String content) {
    List<String> segments = new ArrayList<>();
    String[] lines = content.split("\n", -1);
    StringBuilder current = new StringBuilder();
    boolean inTable = false;

    for (String line : lines) {
      boolean isTableRow = line.trim().startsWith("|");
      if (isTableRow != inTable) {
        if (current.length() > 0) {
          segments.add(current.toString());
          current.setLength(0);
        }
        inTable = isTableRow;
      }
      current.append(line).append("\n");
    }
    if (current.length() > 0) {
      segments.add(current.toString());
    }
    return segments;
  }

  // ---- sliding-window fallback ----

  private List<String> slidingWindow(String text, int chunkSize, int overlap) {
    List<String> chunks = new ArrayList<>();
    String[] paragraphs = text.split("\\n\\n+");
    StringBuilder currentChunk = new StringBuilder();
    int currentTokens = 0;

    for (String paragraph : paragraphs) {
      if (paragraph.length() > MAX_CHARS) {
        if (currentChunk.length() > 0) {
          chunks.add(currentChunk.toString().trim());
          currentChunk = new StringBuilder();
          currentTokens = 0;
        }
        chunks.addAll(splitByCharLimit(paragraph, MAX_CHARS, overlap));
        continue;
      }

      int paraTokens = estimateTokens(paragraph);
      if (currentTokens + paraTokens > chunkSize && currentChunk.length() > 0) {
        chunks.add(currentChunk.toString().trim());
        String overlapText = overlapTail(currentChunk.toString(), overlap);
        currentChunk = new StringBuilder(overlapText);
        currentTokens = estimateTokens(overlapText);
      }

      if (currentChunk.length() + paragraph.length() > MAX_CHARS && currentChunk.length() > 0) {
        chunks.add(currentChunk.toString().trim());
        String overlapText = overlapTail(currentChunk.toString(), overlap);
        currentChunk = new StringBuilder(overlapText);
        currentTokens = estimateTokens(overlapText);

        if (currentChunk.length() + paragraph.length() > MAX_CHARS) {
          if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
            currentChunk = new StringBuilder();
            currentTokens = 0;
          }
          chunks.addAll(splitByCharLimit(paragraph, MAX_CHARS, overlap));
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

  private List<String> splitByCharLimit(String text, int maxChars, int overlap) {
    List<String> chunks = new ArrayList<>();
    String[] sentences = text.split("(?<=[.!?])\\s+");
    StringBuilder currentChunk = new StringBuilder();

    for (String sentence : sentences) {
      if (sentence.length() > maxChars) {
        if (currentChunk.length() > 0) {
          chunks.add(currentChunk.toString().trim());
          currentChunk = new StringBuilder();
        }
        for (int i = 0; i < sentence.length(); i += maxChars - 100) {
          chunks.add(sentence.substring(i, Math.min(i + maxChars - 100, sentence.length())));
        }
        continue;
      }
      if (currentChunk.length() + sentence.length() > maxChars && currentChunk.length() > 0) {
        chunks.add(currentChunk.toString().trim());
        String overlapText = overlapTail(currentChunk.toString(), overlap);
        currentChunk = new StringBuilder(overlapText);
      }
      currentChunk.append(sentence).append(" ");
    }
    if (currentChunk.length() > 0) {
      chunks.add(currentChunk.toString().trim());
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

  private int estimateTokens(String text) {
    return text.length() / 4;
  }

  // ---- image association ----

  /**
   * For each chunk, populate {@code associatedImageIndices} by finding images whose {@code
   * approximateOffset} falls within the character range of the chunk within the full text.
   *
   * <p>This is a best-effort heuristic because offsets produced by different parsers may not be
   * perfectly aligned with the sliding-window character positions.
   */
  private void associateImages(List<RawDocumentChunk> chunks, ParsedDocument document) {
    if (document.images().isEmpty() || chunks.isEmpty()) {
      return;
    }

    // Map each image's approximate char offset to the nearest chunk
    int totalChars = 0;
    List<Integer> chunkStarts = new ArrayList<>();
    for (RawDocumentChunk chunk : chunks) {
      chunkStarts.add(totalChars);
      totalChars += chunk.content().length();
    }

    for (ExtractedImage image : document.images()) {
      int offset = image.approximateOffset();
      // Find the chunk whose start is closest to the image offset
      int bestChunk = 0;
      int bestDist = Integer.MAX_VALUE;
      for (int i = 0; i < chunkStarts.size(); i++) {
        int dist = Math.abs(chunkStarts.get(i) - offset);
        if (dist < bestDist) {
          bestDist = dist;
          bestChunk = i;
        }
      }
      ((List<Integer>) chunks.get(bestChunk).associatedImageIndices()).add(image.index());
    }
  }
}
