package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for calculating confidence scores for RAG retrieval results. Helps prevent hallucinations
 * by detecting low-quality retrievals.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalConfidenceService {

  private final MeterRegistry meterRegistry;

  @Value("${rag.confidence.high-threshold:0.7}")
  private double highThreshold;

  @Value("${rag.confidence.medium-threshold:0.4}")
  private double mediumThreshold;

  @Value("${rag.confidence.max-rrf-weight:0.4}")
  private double maxRrfWeight;

  @Value("${rag.confidence.agreement-weight:0.3}")
  private double agreementWeight;

  @Value("${rag.confidence.coverage-weight:0.2}")
  private double coverageWeight;

  @Value("${rag.confidence.diversity-weight:0.1}")
  private double diversityWeight;

  /**
   * Calculates confidence score for retrieval results.
   *
   * @param vectorResults results from vector search
   * @param bm25Results results from BM25 keyword search
   * @param finalResults final fused results (after RRF)
   * @param query user query
   * @return confidence score with level and explanation
   */
  public ConfidenceScore calculateConfidence(
      List<DocumentChunk> vectorResults,
      List<DocumentChunk> bm25Results,
      List<DocumentChunk> finalResults,
      String query) {

    if (finalResults.isEmpty()) {
      return new ConfidenceScore(0.0, ConfidenceLevel.LOW, "No documents retrieved", List.of());
    }

    // Component 1: Max RRF score (normalized to 0-1)
    double maxRrfScore = normalizeRrfScore(finalResults.get(0).getRelevanceScore());

    // Component 2: Vector-BM25 agreement (% overlap in top results)
    double agreement = calculateAgreement(vectorResults, bm25Results);

    // Component 3: Keyword coverage (% query terms found in top chunk)
    double coverage = calculateKeywordCoverage(query, finalResults.get(0).getContent());

    // Component 4: Diversity (unique documents represented)
    double diversity = calculateDiversity(finalResults);

    // Weighted sum
    double score =
        maxRrfWeight * maxRrfScore
            + agreementWeight * agreement
            + coverageWeight * coverage
            + diversityWeight * diversity;

    ConfidenceLevel level = getLevel(score);
    String explanation = buildExplanation(maxRrfScore, agreement, coverage, diversity, score);

    // Track metrics
    meterRegistry.counter("rag.confidence." + level.name().toLowerCase()).increment();
    meterRegistry.gauge("rag.confidence.score", score);

    log.debug(
        "Confidence score: {} (level: {}) - maxRRF={}, agreement={}, coverage={}, diversity={}",
        String.format("%.3f", score),
        level,
        String.format("%.3f", maxRrfScore),
        String.format("%.3f", agreement),
        String.format("%.3f", coverage),
        String.format("%.3f", diversity));

    return new ConfidenceScore(score, level, explanation, buildComponents(score, level));
  }

  /**
   * Normalizes RRF score to 0-1 range. RRF scores typically range from 0 to ~0.02 (for rank 1).
   * Formula: 1/(60+1) = 0.0164 for best rank.
   */
  private double normalizeRrfScore(Double rrfScore) {
    if (rrfScore == null) {
      return 0.0;
    }
    // RRF score for rank 1 is 1/61 ≈ 0.0164
    // RRF score for rank 10 is 1/70 ≈ 0.0143
    // Map 0.0164 → 1.0, 0.0143 → 0.8, lower scores → lower values
    double normalized = Math.min(1.0, rrfScore / 0.0164);
    return Math.max(0.0, normalized);
  }

  /** Calculates agreement between vector and BM25 results (Jaccard similarity). */
  private double calculateAgreement(
      List<DocumentChunk> vectorResults, List<DocumentChunk> bm25Results) {
    if (vectorResults.isEmpty() || bm25Results.isEmpty()) {
      return 0.0;
    }

    // Get top 10 chunk IDs from each
    Set<String> vectorIds = new HashSet<>();
    Set<String> bm25Ids = new HashSet<>();

    for (int i = 0; i < Math.min(10, vectorResults.size()); i++) {
      vectorIds.add(vectorResults.get(i).getId());
    }
    for (int i = 0; i < Math.min(10, bm25Results.size()); i++) {
      bm25Ids.add(bm25Results.get(i).getId());
    }

    // Calculate Jaccard similarity: intersection / union
    Set<String> intersection = new HashSet<>(vectorIds);
    intersection.retainAll(bm25Ids);

    Set<String> union = new HashSet<>(vectorIds);
    union.addAll(bm25Ids);

    return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
  }

  /** Calculates keyword coverage (% of query terms found in top chunk). */
  private double calculateKeywordCoverage(String query, String topChunkContent) {
    if (query == null || query.isBlank() || topChunkContent == null) {
      return 0.0;
    }

    // Tokenize query (simple whitespace split)
    String[] queryTerms = query.toLowerCase().split("\\s+");
    String contentLower = topChunkContent.toLowerCase();

    int matchedTerms = 0;
    for (String term : queryTerms) {
      // Skip stop words and very short terms
      if (term.length() > 2 && !isStopWord(term)) {
        if (contentLower.contains(term)) {
          matchedTerms++;
        }
      }
    }

    // Avoid division by zero
    long relevantTerms = java.util.Arrays.stream(queryTerms).filter(t -> t.length() > 2).count();
    return relevantTerms == 0 ? 0.0 : (double) matchedTerms / relevantTerms;
  }

  /** Calculates diversity score (unique documents in results). */
  private double calculateDiversity(List<DocumentChunk> results) {
    if (results.isEmpty()) {
      return 0.0;
    }

    Set<String> uniqueDocs = new HashSet<>();
    for (DocumentChunk chunk : results) {
      uniqueDocs.add(chunk.getDocumentId().toString());
    }

    // Normalize: 1 doc = 0.2, 2 docs = 0.4, ..., 5+ docs = 1.0
    return Math.min(1.0, uniqueDocs.size() / 5.0);
  }

  /** Simple stop word check. */
  private boolean isStopWord(String term) {
    Set<String> stopWords =
        Set.of("the", "is", "at", "which", "on", "a", "an", "and", "or", "but", "in", "with");
    return stopWords.contains(term);
  }

  /** Determines confidence level based on score. */
  private ConfidenceLevel getLevel(double score) {
    if (score >= highThreshold) {
      return ConfidenceLevel.HIGH;
    } else if (score >= mediumThreshold) {
      return ConfidenceLevel.MEDIUM;
    } else {
      return ConfidenceLevel.LOW;
    }
  }

  /** Builds human-readable explanation of confidence score. */
  private String buildExplanation(
      double maxRrf, double agreement, double coverage, double diversity, double finalScore) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Confidence: %.1f%% | ", finalScore * 100));

    if (maxRrf > 0.8) {
      sb.append("Strong relevance match");
    } else if (maxRrf > 0.5) {
      sb.append("Moderate relevance");
    } else {
      sb.append("Weak relevance");
    }

    if (agreement > 0.5) {
      sb.append(", high vector-keyword agreement");
    } else if (agreement < 0.2) {
      sb.append(", low vector-keyword agreement");
    }

    if (coverage < 0.3) {
      sb.append(", few query terms matched");
    }

    return sb.toString();
  }

  /** Builds detailed component breakdown for logging. */
  private List<String> buildComponents(double score, ConfidenceLevel level) {
    return List.of(
        String.format("Final score: %.3f (level: %s)", score, level),
        String.format("Thresholds: high=%.2f, medium=%.2f", highThreshold, mediumThreshold));
  }

  /** Confidence level enum. */
  public enum ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
  }

  /** Confidence score result. */
  public record ConfidenceScore(
      double score, ConfidenceLevel level, String explanation, List<String> components) {
    // Backward compatibility constructor
    public ConfidenceScore(double score, ConfidenceLevel level, String explanation) {
      this(score, level, explanation, List.of());
    }
  }
}
