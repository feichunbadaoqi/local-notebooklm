package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cross-encoder reranker that scores query-passage relevance using an LLM. Provides semantic
 * reranking after RRF fusion for significant quality improvements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrossEncoderReranker {

  private final ChatModel chatModel;
  private final MeterRegistry meterRegistry;

  @Value("${rag.reranking.cross-encoder.batch-size:20}")
  private int batchSize;

  @Value("${rag.reranking.cross-encoder.enabled:true}")
  private boolean enabled;

  /**
   * Reranks candidates using cross-encoder scoring (query-passage semantic matching).
   *
   * @param query the search query
   * @param candidates candidate chunks from RRF fusion
   * @param topK number of results to return
   * @return top K reranked chunks with scores
   */
  @Timed(value = "rag.rerank.crossencoder", description = "Time for cross-encoder reranking")
  public List<ScoredChunk> rerank(String query, List<DocumentChunk> candidates, int topK) {
    if (!enabled) {
      log.debug("Cross-encoder reranking disabled, returning candidates as-is");
      return candidates.stream()
          .map(chunk -> new ScoredChunk(chunk, chunk.getRelevanceScore()))
          .limit(topK)
          .toList();
    }

    if (candidates.isEmpty()) {
      log.debug("No candidates to rerank");
      return List.of();
    }

    log.debug(
        "Reranking {} candidates with cross-encoder (batch size: {})",
        candidates.size(),
        batchSize);

    // Batch score passages to avoid context limits
    List<ScoredChunk> scoredChunks = new ArrayList<>();

    for (int i = 0; i < candidates.size(); i += batchSize) {
      int end = Math.min(i + batchSize, candidates.size());
      List<DocumentChunk> batch = candidates.subList(i, end);

      log.debug("Processing batch {}-{} of {}", i, end, candidates.size());
      String prompt = buildRerankPrompt(query, batch);

      try {
        String response = chatModel.chat(prompt);
        List<Double> scores = parseScores(response, batch.size());

        for (int j = 0; j < batch.size(); j++) {
          scoredChunks.add(new ScoredChunk(batch.get(j), scores.get(j)));
        }
      } catch (Exception e) {
        log.warn("Reranking batch {}-{} failed: {}, using fallback scores", i, end, e.getMessage());
        // Fallback: use RRF scores or mid-score (scale RRF score ~0.01-0.03 to 0.1-0.3 range)
        for (DocumentChunk chunk : batch) {
          double fallbackScore =
              chunk.getRelevanceScore() > 0 ? chunk.getRelevanceScore() * 10 : 0.5;
          scoredChunks.add(new ScoredChunk(chunk, fallbackScore));
        }
      }
    }

    // Sort by score descending and return top K
    scoredChunks.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

    // Track metrics
    if (!scoredChunks.isEmpty()) {
      meterRegistry.gauge("rag.rerank.top_score", scoredChunks.get(0).score());
    }
    meterRegistry.counter("rag.rerank.crossencoder.invocations").increment();

    log.debug(
        "Cross-encoder reranking complete, top score: {}",
        scoredChunks.isEmpty() ? "N/A" : String.format("%.3f", scoredChunks.get(0).score()));

    return scoredChunks.stream().limit(topK).toList();
  }

  /**
   * Builds the reranking prompt with query and candidate passages.
   *
   * @param query the search query
   * @param batch batch of candidate chunks
   * @return LLM prompt for scoring
   */
  private String buildRerankPrompt(String query, List<DocumentChunk> batch) {
    StringBuilder sb = new StringBuilder();
    sb.append("Score the relevance of each passage to the query on a scale of 0.0 to 1.0.\n\n");
    sb.append("Instructions:\n");
    sb.append("- 1.0 = Perfectly answers the query with precise information\n");
    sb.append("- 0.7-0.9 = Highly relevant, contains most needed information\n");
    sb.append("- 0.4-0.6 = Somewhat relevant, contains related information\n");
    sb.append("- 0.1-0.3 = Marginally relevant, tangentially related\n");
    sb.append("- 0.0 = Not relevant at all\n\n");

    sb.append("Query: ").append(query).append("\n\n");
    sb.append("Passages:\n");

    for (int i = 0; i < batch.size(); i++) {
      DocumentChunk chunk = batch.get(i);
      String content = chunk.getContent();

      // Truncate very long content to fit in prompt
      if (content.length() > 500) {
        content = content.substring(0, 500) + "...";
      }

      sb.append("[").append(i).append("] ").append(content).append("\n\n");
    }

    sb.append("Return ONLY a comma-separated list of scores (0.0-1.0) in order.\n");
    sb.append("Example: 0.8,0.3,0.9,0.5\n\n");
    sb.append("Scores: ");

    return sb.toString();
  }

  /**
   * Parses LLM response to extract scores.
   *
   * @param response LLM response containing comma-separated scores
   * @param expectedCount expected number of scores
   * @return list of parsed scores
   */
  private List<Double> parseScores(String response, int expectedCount) {
    List<Double> scores = new ArrayList<>();

    // Remove any leading/trailing text and extract just the numbers
    String cleaned = response.trim();

    // Try to find comma-separated numbers
    String[] parts = cleaned.split(",");

    for (String part : parts) {
      try {
        // Extract first number found in this part
        String numberStr = part.trim().replaceAll("[^0-9.]", "");
        if (!numberStr.isEmpty()) {
          double score = Double.parseDouble(numberStr);
          // Clamp to [0.0, 1.0]
          score = Math.max(0.0, Math.min(1.0, score));
          scores.add(score);
        }
      } catch (NumberFormatException e) {
        log.debug("Failed to parse score from: '{}', using default 0.5", part);
        scores.add(0.5); // Default mid-score
      }
    }

    // Pad or truncate to expected count
    while (scores.size() < expectedCount) {
      scores.add(0.5); // Default mid-score for missing values
    }

    if (scores.size() > expectedCount) {
      log.warn("Received {} scores but expected {}, truncating", scores.size(), expectedCount);
      scores = scores.subList(0, expectedCount);
    }

    return scores;
  }

  /** Scored chunk with relevance score. */
  public record ScoredChunk(DocumentChunk chunk, double score) {}
}
