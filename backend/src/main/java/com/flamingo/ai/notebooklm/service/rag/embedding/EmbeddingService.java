package com.flamingo.ai.notebooklm.service.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Service for generating text embeddings using OpenAI's embedding model. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

  // OpenAI text-embedding-3-small has 8192 token limit
  // Use VERY conservative estimate for multilingual content (especially CJK)
  // Dense CJK text can be as low as 1.0 chars/token (nearly 1:1 ratio)
  private static final int MAX_TOKENS_PER_EMBEDDING = 5000; // Leave safe margin below 8192 limit
  private static final double CHARS_PER_TOKEN_ESTIMATE = 1.0; // Very conservative for dense CJK
  private static final int MAX_CHARS_PER_EMBEDDING =
      (int) (MAX_TOKENS_PER_EMBEDDING * CHARS_PER_TOKEN_ESTIMATE); // 5000 chars

  // Instruction prefixes for better semantic matching (especially for multilingual models)
  private static final String QUERY_PREFIX =
      "Represent this question for retrieving relevant document passages: ";
  private static final String PASSAGE_PREFIX = "Represent this document passage for retrieval: ";

  private final EmbeddingModel embeddingModel;
  private final MeterRegistry meterRegistry;

  /**
   * Embeds a query text with query-specific instruction prefix for better retrieval matching.
   *
   * @param query the query text
   * @return embedding vector
   */
  @Timed(value = "embedding.embedQuery", description = "Time to embed query")
  @CircuitBreaker(name = "openai", fallbackMethod = "embedTextFallback")
  @Retry(name = "openai")
  public List<Float> embedQuery(String query) {
    String prefixedQuery = QUERY_PREFIX + query;
    log.debug("embedQuery called, input length: {} chars (with prefix)", prefixedQuery.length());

    // Truncate if too long (account for prefix in length)
    if (prefixedQuery.length() > MAX_CHARS_PER_EMBEDDING) {
      log.warn(
          "Query too long for embedding, truncating from {} chars to {} chars",
          prefixedQuery.length(),
          MAX_CHARS_PER_EMBEDDING);
      prefixedQuery = prefixedQuery.substring(0, MAX_CHARS_PER_EMBEDDING);
    }

    log.debug("Calling OpenAI embedding API for query...");
    Response<Embedding> response = embeddingModel.embed(prefixedQuery);
    meterRegistry.counter("embedding.requests.success", "type", "query").increment();

    return toFloatList(response.content().vector());
  }

  /**
   * Embeds a passage text with passage-specific instruction prefix for better retrieval matching.
   *
   * @param passage the passage text
   * @return embedding vector
   */
  @Timed(value = "embedding.embedPassage", description = "Time to embed passage")
  @CircuitBreaker(name = "openai", fallbackMethod = "embedTextFallback")
  @Retry(name = "openai")
  public List<Float> embedPassage(String passage) {
    String prefixedPassage = PASSAGE_PREFIX + passage;
    log.debug(
        "embedPassage called, input length: {} chars (with prefix)", prefixedPassage.length());

    // Truncate if too long (account for prefix in length)
    if (prefixedPassage.length() > MAX_CHARS_PER_EMBEDDING) {
      log.warn(
          "Passage too long for embedding, truncating from {} chars to {} chars",
          prefixedPassage.length(),
          MAX_CHARS_PER_EMBEDDING);
      prefixedPassage = prefixedPassage.substring(0, MAX_CHARS_PER_EMBEDDING);
    }

    log.debug("Calling OpenAI embedding API for passage...");
    Response<Embedding> response = embeddingModel.embed(prefixedPassage);
    meterRegistry.counter("embedding.requests.success", "type", "passage").increment();

    return toFloatList(response.content().vector());
  }

  /**
   * Embeds text without specific instruction prefix. Deprecated - use embedQuery() or
   * embedPassage() instead for better retrieval performance.
   *
   * @param text the text to embed
   * @return embedding vector
   * @deprecated Use {@link #embedQuery(String)} for queries or {@link #embedPassage(String)} for
   *     passages
   */
  @Deprecated
  @Timed(value = "embedding.embed", description = "Time to embed text")
  @CircuitBreaker(name = "openai", fallbackMethod = "embedTextFallback")
  @Retry(name = "openai")
  public List<Float> embedText(String text) {
    log.debug("embedText called (deprecated), input length: {} chars", text.length());
    // Default to passage embedding for backward compatibility
    return embedPassage(text);
  }

  /** Converts float array to Float list. */
  private List<Float> toFloatList(float[] vector) {
    List<Float> result = new ArrayList<>(vector.length);
    for (float f : vector) {
      result.add(f);
    }
    return result;
  }

  /**
   * Embeds multiple passages (document chunks) with passage-specific instruction prefix.
   *
   * @param texts the list of passages to embed
   * @return list of embedding vectors
   */
  @Timed(value = "embedding.embedBatch", description = "Time to embed batch")
  @CircuitBreaker(name = "openai", fallbackMethod = "embedTextsFallback")
  @Retry(name = "openai")
  public List<List<Float>> embedTexts(List<String> texts) {
    List<List<Float>> results = new ArrayList<>();
    // Process embeddings one at a time to avoid batching issues
    for (int i = 0; i < texts.size(); i++) {
      String text = texts.get(i);
      String prefixedText = PASSAGE_PREFIX + text;

      // Truncate if too long - use conservative estimate
      if (prefixedText.length() > MAX_CHARS_PER_EMBEDDING) {
        log.warn(
            "Passage {} too long for embedding, truncating from {} chars to {} chars",
            i,
            prefixedText.length(),
            MAX_CHARS_PER_EMBEDDING);
        prefixedText = prefixedText.substring(0, MAX_CHARS_PER_EMBEDDING);
      }

      Response<Embedding> response = embeddingModel.embed(prefixedText);
      results.add(toFloatList(response.content().vector()));
    }
    meterRegistry
        .counter("embedding.requests.success", "count", String.valueOf(texts.size()))
        .increment();
    return results;
  }

  @SuppressWarnings("unused")
  private List<Float> embedTextFallback(String text, Throwable t) {
    log.error("Embedding failed for text, circuit breaker open: {}", t.getMessage());
    meterRegistry.counter("embedding.requests.failure").increment();
    return List.of();
  }

  @SuppressWarnings("unused")
  private List<List<Float>> embedTextsFallback(List<String> texts, Throwable t) {
    log.error("Batch embedding failed, circuit breaker open: {}", t.getMessage());
    meterRegistry
        .counter("embedding.requests.failure", "count", String.valueOf(texts.size()))
        .increment();
    return List.of();
  }
}
