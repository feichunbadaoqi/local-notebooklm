package com.flamingo.ai.notebooklm.service.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.inference.RankedDocument;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * True cross-encoder reranking using Elasticsearch Inference API with Elastic Rerank model.
 *
 * <p>Uses DeBERTa v3-based cross-encoder to score query-document relevance. Provides ~40%
 * improvement in ranking quality vs BM25 alone (per Elastic benchmarks).
 *
 * <p><b>Note:</b> Requires Elasticsearch 8.17+ with Inference API enabled and an Elastic Rerank
 * endpoint configured. If the inference API is not available, falls back gracefully to RRF scores.
 *
 * <p><b>Setup:</b> Create the Elastic Rerank endpoint once:
 *
 * <pre>
 * curl -X PUT "http://localhost:9200/_inference/rerank/elastic-rerank" \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *     "service": "elasticsearch",
 *     "service_settings": {
 *       "model_id": ".rerank-v1-elasticsearch",
 *       "task_type": "rerank"
 *     }
 *   }'
 * </pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CrossEncoderRerankService {

  private final ElasticsearchClient elasticsearchClient;
  private final MeterRegistry meterRegistry;
  private final RagConfig ragConfig;

  /** Scored chunk with relevance score. */
  public record ScoredChunk(DocumentChunk chunk, double score) {}

  /**
   * Reranks candidates using true cross-encoder scoring via Elasticsearch Inference API.
   *
   * @param query the search query
   * @param candidates candidate chunks from RRF fusion
   * @param topK number of results to return
   * @return top K reranked chunks with scores
   */
  @Timed(value = "rag.rerank.crossencoder", description = "Cross-encoder reranking time")
  public List<ScoredChunk> rerank(String query, List<DocumentChunk> candidates, int topK) {
    if (!ragConfig.getReranking().getCrossEncoder().isEnabled()) {
      log.debug("Cross-encoder reranking disabled, returning candidates with original scores");
      return candidates.stream()
          .map(chunk -> new ScoredChunk(chunk, chunk.getRelevanceScore()))
          .limit(topK)
          .toList();
    }

    if (candidates.isEmpty()) {
      log.debug("No candidates to rerank");
      return List.of();
    }

    String modelId = ragConfig.getReranking().getCrossEncoder().getModelId();
    log.debug("Reranking {} candidates with cross-encoder (model: {})", candidates.size(), modelId);

    try {
      // Extract document texts for reranking, preferring enriched content
      List<String> documentTexts =
          candidates.stream()
              .map(
                  chunk ->
                      chunk.getEnrichedContent() != null
                          ? chunk.getEnrichedContent()
                          : chunk.getContent())
              .toList();

      // Call Elasticsearch Inference API for reranking
      var response =
          elasticsearchClient
              .inference()
              .rerank(r -> r.inferenceId(modelId).query(query).input(documentTexts));

      // Map reranked results back to original chunks
      List<ScoredChunk> reranked = mapRerankResults(response.rerank(), candidates);

      meterRegistry.counter("rag.rerank.crossencoder.invocations").increment();
      if (!reranked.isEmpty()) {
        meterRegistry.gauge("rag.rerank.crossencoder.top_score", reranked.get(0).score());
      }

      log.debug(
          "Cross-encoder reranked {} candidates, top score: {}",
          reranked.size(),
          reranked.isEmpty() ? 0.0 : reranked.get(0).score());

      return reranked;

    } catch (Exception e) {
      log.warn(
          "Cross-encoder reranking failed ({}), falling back to original scores: {}",
          e.getClass().getSimpleName(),
          e.getMessage());
      meterRegistry.counter("rag.rerank.crossencoder.failures").increment();

      // Graceful fallback to RRF scores
      return candidates.stream()
          .map(chunk -> new ScoredChunk(chunk, chunk.getRelevanceScore()))
          .limit(topK)
          .toList();
    }
  }

  /**
   * Maps Elasticsearch inference API rerank results back to DocumentChunk objects.
   *
   * @param rerankResults rerank results from the inference API
   * @param candidates original candidate list (for index-based lookup)
   * @return list of scored chunks in relevance-score order
   */
  private List<ScoredChunk> mapRerankResults(
      List<RankedDocument> rerankResults, List<DocumentChunk> candidates) {
    List<ScoredChunk> scored = new ArrayList<>(rerankResults.size());

    for (RankedDocument result : rerankResults) {
      int index = result.index();
      if (index >= 0 && index < candidates.size()) {
        double score = result.relevanceScore();
        DocumentChunk chunk = candidates.get(index);
        chunk.setRelevanceScore(score);
        scored.add(new ScoredChunk(chunk, score));
      } else {
        log.warn(
            "Rerank result index {} out of bounds (candidates size: {})", index, candidates.size());
      }
    }

    return scored;
  }
}
