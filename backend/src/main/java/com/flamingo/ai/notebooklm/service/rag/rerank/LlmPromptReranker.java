package com.flamingo.ai.notebooklm.service.rag.rerank;

import com.flamingo.ai.notebooklm.agent.CrossEncoderRerankerAgent;
import com.flamingo.ai.notebooklm.agent.dto.RerankingScores;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * LLM prompt-based reranking using OpenAI chat model. Scores query-passage relevance by calling
 * OpenAI's chat model to evaluate relevance via a structured prompt.
 *
 * <p>This is NOT a true cross-encoder model. For fast, deterministic cross-encoder reranking, use
 * {@link TeiCrossEncoderReranker} (the default strategy) instead.
 *
 * @deprecated Prefer TEI cross-encoder reranker (default). This LLM-based approach is slower (~1-3s
 *     per API call), more expensive (token costs), and non-deterministic. Select with {@code
 *     rag.reranking.strategy=llm}.
 */
@Deprecated(since = "TEI cross-encoder reranker available")
@Service
@ConditionalOnProperty(name = "rag.reranking.strategy", havingValue = "llm")
@RequiredArgsConstructor
@Slf4j
public class LlmPromptReranker implements Reranker {

  private final CrossEncoderRerankerAgent agent;
  private final MeterRegistry meterRegistry;

  @Value("${rag.reranking.llm.batch-size:20}")
  private int batchSize;

  @Value("${rag.reranking.llm.enabled:false}")
  private boolean enabled;

  @Override
  @Timed(value = "rag.rerank.llm", description = "Time for LLM reranking")
  public List<ScoredChunk> rerank(String query, List<DocumentChunk> candidates, int topK) {
    if (!enabled) {
      log.debug("LLM reranking disabled, returning candidates as-is");
      return candidates.stream()
          .map(chunk -> new ScoredChunk(chunk, chunk.getRelevanceScore()))
          .limit(topK)
          .toList();
    }

    if (candidates.isEmpty()) {
      log.debug("No candidates to rerank");
      return List.of();
    }

    log.debug("Reranking {} candidates with LLM (batch size: {})", candidates.size(), batchSize);

    // Batch score passages to avoid context limits
    List<ScoredChunk> scoredChunks = new ArrayList<>();

    for (int i = 0; i < candidates.size(); i += batchSize) {
      int end = Math.min(i + batchSize, candidates.size());
      List<DocumentChunk> batch = candidates.subList(i, end);

      log.debug("Processing batch {}-{} of {}", i, end, candidates.size());
      String passages = buildPassagesString(batch);

      try {
        RerankingScores result = agent.scorePassages(query, passages);
        List<Double> scores = result.scores();

        for (int j = 0; j < batch.size(); j++) {
          double score = j < scores.size() ? Math.max(0.0, Math.min(1.0, scores.get(j))) : 0.5;
          scoredChunks.add(new ScoredChunk(batch.get(j), score));
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
      meterRegistry.gauge("rag.rerank.llm.top_score", scoredChunks.get(0).score());
    }
    meterRegistry.counter("rag.rerank.llm.invocations").increment();

    log.debug(
        "LLM reranking complete, top score: {}",
        scoredChunks.isEmpty() ? "N/A" : String.format("%.3f", scoredChunks.get(0).score()));

    return scoredChunks.stream().limit(topK).toList();
  }

  private String buildPassagesString(List<DocumentChunk> batch) {
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < batch.size(); i++) {
      DocumentChunk chunk = batch.get(i);
      String content = chunk.getContent();

      // Truncate very long content to fit in prompt
      if (content.length() > 500) {
        content = content.substring(0, 500) + "...";
      }

      sb.append("[").append(i).append("] ").append(content).append("\n\n");
    }

    return sb.toString();
  }
}
