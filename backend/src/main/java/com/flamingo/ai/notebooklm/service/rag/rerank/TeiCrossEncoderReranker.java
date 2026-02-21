package com.flamingo.ai.notebooklm.service.rag.rerank;

import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Cross-encoder reranker using Hugging Face TEI (Text Embeddings Inference). Default reranker
 * strategy — fast (~50-200ms on CPU), deterministic, and zero API cost compared to LLM-based
 * reranking.
 */
@Service
@ConditionalOnProperty(name = "rag.reranking.strategy", havingValue = "tei", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class TeiCrossEncoderReranker implements Reranker {

  private final TeiRerankerClient teiRerankerClient;
  private final MeterRegistry meterRegistry;

  @Override
  @Timed(value = "rag.reranker.tei", description = "Time for TEI cross-encoder reranking")
  @CircuitBreaker(name = "tei", fallbackMethod = "rerankFallback")
  @Retry(name = "tei")
  public List<ScoredChunk> rerank(String query, List<DocumentChunk> candidates, int topK) {
    if (candidates.isEmpty()) {
      log.debug("No candidates to rerank");
      return List.of();
    }

    log.debug("TEI reranking {} candidates for query: {}", candidates.size(), query);

    List<String> texts = candidates.stream().map(DocumentChunk::getContent).toList();

    List<TeiRerankerClient.RerankResult> results = teiRerankerClient.rerank(query, texts);

    // Map TEI results back to ScoredChunks
    List<ScoredChunk> scoredChunks =
        results.stream()
            .map(r -> new ScoredChunk(candidates.get(r.index()), r.score()))
            .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
            .limit(topK)
            .toList();

    if (!scoredChunks.isEmpty()) {
      meterRegistry.gauge("rag.reranker.tei.top_score", scoredChunks.get(0).score());
    }
    meterRegistry.counter("rag.reranker.tei.invocations").increment();

    log.debug(
        "TEI reranking complete, top score: {}",
        scoredChunks.isEmpty() ? "N/A" : String.format("%.3f", scoredChunks.get(0).score()));

    return scoredChunks;
  }

  /**
   * Fallback when TEI is unavailable: returns candidates as-is with their existing RRF scores. RRF
   * results are already reasonable, so this is a safe degradation.
   */
  @SuppressWarnings("unused")
  List<ScoredChunk> rerankFallback(
      String query, List<DocumentChunk> candidates, int topK, Throwable t) {
    log.warn("TEI reranker unavailable, using RRF scores as fallback: {}", t.getMessage());
    meterRegistry.counter("rag.reranker.tei.fallback").increment();

    return candidates.stream()
        .map(chunk -> new ScoredChunk(chunk, chunk.getRelevanceScore()))
        .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
        .limit(topK)
        .toList();
  }
}
