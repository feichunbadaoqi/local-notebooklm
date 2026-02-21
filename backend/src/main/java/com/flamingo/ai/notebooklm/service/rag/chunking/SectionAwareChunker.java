package com.flamingo.ai.notebooklm.service.rag.chunking;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.service.rag.image.ImageGroupingStrategy;
import com.flamingo.ai.notebooklm.service.rag.model.DocumentSection;
import com.flamingo.ai.notebooklm.service.rag.model.ExtractedImage;
import com.flamingo.ai.notebooklm.service.rag.model.ParsedDocument;
import com.flamingo.ai.notebooklm.service.rag.model.RawDocumentChunk;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SectionAwareChunker implements DocumentChunker {

  private final ImageGroupingStrategy imageGroupingStrategy;

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
      int cumulativeOffset = 0;
      for (int i = 0; i < slidingChunks.size(); i++) {
        result.add(
            new RawDocumentChunk(slidingChunks.get(i), List.of(), i, List.of(), cumulativeOffset));
        cumulativeOffset += slidingChunks.get(i).length();
      }
    } else {
      walkSections(document.sections(), document, chunkSize, overlap, result);

      // Defensive fallback: sections exist but all have empty content
      if (result.isEmpty() && document.fullText() != null && !document.fullText().isBlank()) {
        log.warn(
            "All {} sections produced 0 chunks — falling back to fullText sliding window",
            document.sections().size());
        List<String> slidingChunks = slidingWindow(document.fullText(), chunkSize, overlap);
        int cumulativeOffset = 0;
        for (int i = 0; i < slidingChunks.size(); i++) {
          result.add(
              new RawDocumentChunk(
                  slidingChunks.get(i), List.of(), i, List.of(), cumulativeOffset));
          cumulativeOffset += slidingChunks.get(i).length();
        }
      }
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
        int relativeOffset = 0;
        for (String chunk : sectionChunks) {
          int idx = result.size();
          int docOffset = section.startOffset() + relativeOffset;
          result.add(new RawDocumentChunk(chunk, breadcrumb, idx, new ArrayList<>(), docOffset));
          relativeOffset += chunk.length();
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
   * Associates images with chunks using the configured grouping strategy.
   *
   * <p>The strategy first groups related images (e.g., icons in a diagram), then assigns each group
   * to the same chunk. Ungrouped images are assigned individually using the nearest-distance
   * heuristic.
   *
   * <p>This approach prevents diagram fragmentation across multiple chunks.
   */
  private void associateImages(List<RawDocumentChunk> chunks, ParsedDocument document) {
    if (document.images().isEmpty() || chunks.isEmpty()) {
      return;
    }

    log.debug("Using {} strategy for image grouping", imageGroupingStrategy.getStrategyName());

    // Apply grouping strategy to images
    List<ExtractedImage> groupedImages = imageGroupingStrategy.groupImages(document.images());

    // Build chunk start offsets for distance calculations (using document offsets)
    List<Integer> chunkStarts = new ArrayList<>();
    for (RawDocumentChunk chunk : chunks) {
      chunkStarts.add(chunk.documentOffset());
    }

    // Partition images into grouped and ungrouped (LinkedHashMap preserves insertion order)
    Map<Integer, List<ExtractedImage>> imageGroups = new LinkedHashMap<>();
    List<ExtractedImage> ungroupedImages = new ArrayList<>();

    for (ExtractedImage image : groupedImages) {
      if (image.spatialGroupId() >= 0) {
        imageGroups.computeIfAbsent(image.spatialGroupId(), k -> new ArrayList<>()).add(image);
      } else {
        ungroupedImages.add(image);
      }
    }

    // Assign grouped images: all images in a group go to the same chunk
    for (Map.Entry<Integer, List<ExtractedImage>> entry : imageGroups.entrySet()) {
      List<ExtractedImage> group = entry.getValue();
      // Use the first image's offset as representative for the group
      int representativeOffset = group.get(0).approximateOffset();
      int bestChunk = findNearestChunk(representativeOffset, chunkStarts);

      // Add only the first image's index as representative — all indices in a spatial group
      // map to the same composite image UUID, so one reference is sufficient.
      ((List<Integer>) chunks.get(bestChunk).associatedImageIndices()).add(group.get(0).index());

      log.debug(
          "Assigned spatial group {} ({} images) to chunk {}",
          entry.getKey(),
          group.size(),
          bestChunk);
    }

    // Assign ungrouped images individually
    for (ExtractedImage image : ungroupedImages) {
      int bestChunk = findNearestChunk(image.approximateOffset(), chunkStarts);
      ((List<Integer>) chunks.get(bestChunk).associatedImageIndices()).add(image.index());
    }

    log.debug(
        "Associated {} images ({} groups, {} ungrouped) with {} chunks",
        groupedImages.size(),
        imageGroups.size(),
        ungroupedImages.size(),
        chunks.size());
  }

  /**
   * Finds the chunk index whose start offset is closest to the given offset.
   *
   * @param offset character offset to match
   * @param chunkStarts list of chunk start offsets
   * @return index of the nearest chunk
   */
  private int findNearestChunk(int offset, List<Integer> chunkStarts) {
    int bestChunk = 0;
    int bestDist = Integer.MAX_VALUE;
    for (int i = 0; i < chunkStarts.size(); i++) {
      int dist = Math.abs(chunkStarts.get(i) - offset);
      if (dist < bestDist) {
        bestDist = dist;
        bestChunk = i;
      }
    }
    return bestChunk;
  }
}
