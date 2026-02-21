package com.flamingo.ai.notebooklm.service.rag.rerank;

import com.flamingo.ai.notebooklm.config.RagConfig;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * HTTP client for Hugging Face TEI (Text Embeddings Inference) reranking endpoint. Encapsulates all
 * WebClient communication with the TEI container.
 */
@Component
@ConditionalOnProperty(name = "rag.reranking.strategy", havingValue = "tei", matchIfMissing = true)
@Slf4j
public class TeiRerankerClient {

  private final WebClient webClient;
  private final int readTimeoutMs;
  private final boolean rawScores;
  private final boolean truncate;

  public TeiRerankerClient(RagConfig ragConfig) {
    RagConfig.Reranking.Tei tei = ragConfig.getReranking().getTei();
    this.readTimeoutMs = tei.getReadTimeoutMs();
    this.rawScores = tei.isRawScores();
    this.truncate = tei.isTruncate();
    this.webClient =
        WebClient.builder()
            .baseUrl(tei.getBaseUrl())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    log.info(
        "TEI reranker client initialized: baseUrl={}, model={}",
        tei.getBaseUrl(),
        tei.getModelId());
  }

  /**
   * Calls TEI /rerank endpoint to score texts against a query.
   *
   * @param query the search query
   * @param texts the candidate texts to score
   * @return list of results with index and score, sorted by score descending by TEI
   */
  public List<RerankResult> rerank(String query, List<String> texts) {
    var request = new TeiRerankRequest(query, texts, rawScores, truncate);
    return webClient
        .post()
        .uri("/rerank")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .retrieve()
        .bodyToFlux(RerankResult.class)
        .collectList()
        .timeout(Duration.ofMillis(readTimeoutMs))
        .block();
  }

  record TeiRerankRequest(String query, List<String> texts, boolean raw_scores, boolean truncate) {}

  /** TEI rerank response element. */
  public record RerankResult(int index, double score) {}
}
