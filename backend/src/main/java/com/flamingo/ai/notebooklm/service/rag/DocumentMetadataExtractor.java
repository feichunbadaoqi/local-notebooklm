package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.config.RagConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Extracts structured metadata from documents for enhanced RAG retrieval.
 *
 * <p>Extracts: - Document title (from headings or filename) - Section headers - Keywords via TF-IDF
 * - Builds enriched content with metadata prefix
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentMetadataExtractor {

  private final RagConfig ragConfig;

  // Common English stop words
  private static final Set<String> STOP_WORDS =
      Set.of(
          "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "he", "in", "is",
          "it", "its", "of", "on", "or", "that", "the", "to", "was", "were", "will", "with", "this",
          "but", "they", "have", "had", "what", "when", "where", "who", "which", "why", "how",
          "all", "each", "every", "both", "few", "more", "most", "other", "some", "such", "no",
          "nor", "not", "only", "own", "same", "so", "than", "too", "very", "just", "can", "should",
          "now", "been", "being", "do", "does", "did", "doing", "would", "could", "might", "must",
          "shall", "may", "about", "above", "after", "again", "against", "before", "below",
          "between", "down", "during", "into", "over", "through", "under", "until", "up", "while",
          "am", "i", "me", "my", "we", "our", "you", "your", "him", "her", "them", "their", "if",
          "then", "also", "here", "there", "these", "those", "else", "any", "many", "much", "even");

  // Patterns for section headers (Markdown, common document formats)
  private static final Pattern MARKDOWN_HEADER =
      Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
  private static final Pattern UNDERLINE_HEADER =
      Pattern.compile("^(.+)\\n[=\\-]{3,}$", Pattern.MULTILINE);
  private static final Pattern NUMBERED_HEADER =
      Pattern.compile("^\\d+\\.\\s+([A-Z].+)$", Pattern.MULTILINE);
  private static final Pattern CAPS_HEADER =
      Pattern.compile("^([A-Z][A-Z\\s]{5,})$", Pattern.MULTILINE);

  /**
   * Extracts a document title from content or falls back to filename.
   *
   * @param content the document content
   * @param fileName the document filename
   * @return extracted title
   */
  public String extractTitle(String content, String fileName) {
    if (content == null || content.isEmpty()) {
      return cleanFileName(fileName);
    }

    // Try to find first heading
    Matcher matcher = MARKDOWN_HEADER.matcher(content);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }

    // Try underline-style header
    matcher = UNDERLINE_HEADER.matcher(content);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }

    // Try numbered header
    matcher = NUMBERED_HEADER.matcher(content);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }

    // Fall back to first non-empty line if it looks like a title
    String[] lines = content.split("\\n");
    for (String line : lines) {
      line = line.trim();
      if (!line.isEmpty() && line.length() < 200 && !line.contains(". ")) {
        return line;
      }
    }

    return cleanFileName(fileName);
  }

  /**
   * Extracts section headers from document content.
   *
   * @param content the document content
   * @return list of section headers in order
   */
  public List<String> extractSections(String content) {
    if (content == null || content.isEmpty()) {
      return List.of();
    }

    List<String> sections = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    // Extract Markdown headers
    Matcher matcher = MARKDOWN_HEADER.matcher(content);
    while (matcher.find()) {
      String header = matcher.group(1).trim();
      if (!seen.contains(header.toLowerCase())) {
        sections.add(header);
        seen.add(header.toLowerCase());
      }
    }

    // Extract underline-style headers
    matcher = UNDERLINE_HEADER.matcher(content);
    while (matcher.find()) {
      String header = matcher.group(1).trim();
      if (!seen.contains(header.toLowerCase())) {
        sections.add(header);
        seen.add(header.toLowerCase());
      }
    }

    // Extract numbered headers
    matcher = NUMBERED_HEADER.matcher(content);
    while (matcher.find()) {
      String header = matcher.group(1).trim();
      if (!seen.contains(header.toLowerCase())) {
        sections.add(header);
        seen.add(header.toLowerCase());
      }
    }

    // Extract ALL-CAPS headers
    matcher = CAPS_HEADER.matcher(content);
    while (matcher.find()) {
      String header = matcher.group(1).trim();
      if (header.length() > 3 && !seen.contains(header.toLowerCase())) {
        sections.add(header);
        seen.add(header.toLowerCase());
      }
    }

    return sections;
  }

  /**
   * Finds which section a specific chunk belongs to based on its position.
   *
   * @param content full document content
   * @param chunkStart the starting character position of the chunk
   * @return the section title this chunk belongs to, or null if none
   */
  public String findChunkSection(String content, int chunkStart) {
    if (content == null || content.isEmpty() || chunkStart < 0) {
      return null;
    }

    String precedingContent = content.substring(0, Math.min(chunkStart, content.length()));
    String lastSection = null;

    // Find the last section header before this chunk position
    for (Pattern pattern :
        List.of(MARKDOWN_HEADER, UNDERLINE_HEADER, NUMBERED_HEADER, CAPS_HEADER)) {
      Matcher matcher = pattern.matcher(precedingContent);
      while (matcher.find()) {
        lastSection = matcher.group(1).trim();
      }
    }

    return lastSection;
  }

  /**
   * Extracts top keywords from content using TF-IDF approximation.
   *
   * @param content the text content
   * @param topN number of keywords to return
   * @return list of top keywords
   */
  public List<String> extractKeywords(String content, int topN) {
    if (content == null || content.isEmpty()) {
      return List.of();
    }

    // Tokenize and clean
    List<String> tokens = tokenize(content.toLowerCase());
    tokens = removeStopwords(tokens);

    if (tokens.isEmpty()) {
      return List.of();
    }

    // Calculate term frequency
    Map<String, Integer> tf = new HashMap<>();
    for (String token : tokens) {
      tf.merge(token, 1, Integer::sum);
    }

    // Calculate TF-IDF scores (simple approximation)
    Map<String, Double> scores = new HashMap<>();
    int totalTokens = tokens.size();

    for (Map.Entry<String, Integer> entry : tf.entrySet()) {
      String term = entry.getKey();
      int freq = entry.getValue();

      // Skip very short terms, but be lenient for CJK (denser information)
      boolean isCJK =
          term.chars()
              .anyMatch(
                  c ->
                      Character.UnicodeBlock.of(c)
                          == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS);
      int minLength = isCJK ? 2 : 3; // Chinese words can be 2+ chars, English needs 3+

      if (term.length() < minLength) {
        continue;
      }

      // TF: log-normalized
      double tfScore = 1 + Math.log(freq);

      // Length bonus for longer terms (likely more specific)
      // CJK: 3+ chars is significant, English: 6+ chars
      int longTermThreshold = isCJK ? 3 : 6;
      double lengthBonus = term.length() >= longTermThreshold ? 1.2 : 1.0;

      // Penalty for very common terms (appear in > 10% of tokens)
      double frequency = (double) freq / totalTokens;
      double frequencyPenalty = frequency > 0.1 ? 0.5 : 1.0;

      scores.put(term, tfScore * lengthBonus * frequencyPenalty);
    }

    // Return top N
    return scores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(topN)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  /**
   * Extracts keywords with default count from configuration.
   *
   * @param content the text content
   * @return list of keywords
   */
  public List<String> extractKeywords(String content) {
    int maxKeywords = ragConfig.getMetadata().getMaxKeywords();
    return extractKeywords(content, maxKeywords);
  }

  /**
   * Builds enriched content by prefixing metadata to improve embedding quality.
   *
   * <p>Format: [Document: {title}] [Section: {section}] [Keywords: {keywords}]
   *
   * <p>{content}
   *
   * @param content the raw chunk content
   * @param documentTitle the document title
   * @param sectionTitle the section title (may be null)
   * @param keywords the extracted keywords
   * @return enriched content string
   */
  public String buildEnrichedContent(
      String content, String documentTitle, String sectionTitle, List<String> keywords) {
    StringBuilder enriched = new StringBuilder();

    if (documentTitle != null && !documentTitle.isEmpty()) {
      enriched.append("[Document: ").append(documentTitle).append("]\n");
    }

    if (sectionTitle != null && !sectionTitle.isEmpty()) {
      enriched.append("[Section: ").append(sectionTitle).append("]\n");
    }

    if (keywords != null && !keywords.isEmpty()) {
      enriched.append("[Keywords: ").append(String.join(", ", keywords)).append("]\n");
    }

    if (enriched.length() > 0) {
      enriched.append("\n");
    }
    enriched.append(content);

    return enriched.toString();
  }

  private List<String> tokenize(String text) {
    // Split on whitespace and punctuation, keeping Unicode word characters (including CJK)
    // \\p{L} = any Unicode letter (including Chinese, Japanese, Korean, etc.)
    // \\p{N} = any Unicode number
    String[] words = text.split("[^\\p{L}\\p{N}']+");
    return Arrays.stream(words)
        .filter(w -> !w.isEmpty())
        .map(w -> w.replaceAll("^'+|'+$", "")) // Remove leading/trailing apostrophes
        .filter(w -> !w.isEmpty())
        .filter(w -> w.length() >= 2) // Keep words with at least 2 characters (Chinese or English)
        .collect(Collectors.toList());
  }

  private List<String> removeStopwords(List<String> tokens) {
    return tokens.stream().filter(t -> !STOP_WORDS.contains(t)).collect(Collectors.toList());
  }

  private String cleanFileName(String fileName) {
    if (fileName == null) {
      return "Unknown Document";
    }
    // Remove extension and replace underscores/dashes with spaces
    String name = fileName.replaceFirst("\\.[^.]+$", "");
    name = name.replaceAll("[_-]", " ");
    // Capitalize first letter
    if (!name.isEmpty()) {
      name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }
}
