package com.flamingo.ai.notebooklm.service.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

  private final EmbeddingModel embeddingModel;
  private final MeterRegistry meterRegistry;

  @CircuitBreaker(name = "openai", fallbackMethod = "embedTextFallback")
  @Retry(name = "openai")
  public List<Float> embedText(String text) {
    log.debug("embedText called, input length: {} chars", text.length());
    // Truncate if too long - use conservative estimate
    if (text.length() > MAX_CHARS_PER_EMBEDDING) {
      log.warn(
          "Text too long for embedding, truncating from {} chars to {} chars",
          text.length(),
          MAX_CHARS_PER_EMBEDDING);
      text = text.substring(0, MAX_CHARS_PER_EMBEDDING);
    }
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      log.debug("Calling OpenAI embedding API...");
      Response<Embedding> response = embeddingModel.embed(text);
      meterRegistry.counter("embedding.requests.success").increment();

      float[] vector = response.content().vector();
      log.debug("Embedding generated successfully, vector dimension: {}", vector.length);
      List<Float> result = new ArrayList<>(vector.length);
      for (float f : vector) {
        result.add(f);
      }
      return result;
    } finally {
      sample.stop(meterRegistry.timer("embedding.duration"));
    }
  }

  @CircuitBreaker(name = "openai", fallbackMethod = "embedTextsFallback")
  @Retry(name = "openai")
  public List<List<Float>> embedTexts(List<String> texts) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      List<List<Float>> results = new ArrayList<>();
      // Process embeddings one at a time to avoid batching issues
      for (int i = 0; i < texts.size(); i++) {
        String text = texts.get(i);
        // Truncate if too long - use conservative estimate
        String truncatedText = text;
        if (text.length() > MAX_CHARS_PER_EMBEDDING) {
          log.warn(
              "Chunk {} too long for embedding, truncating from {} chars to {} chars",
              i,
              text.length(),
              MAX_CHARS_PER_EMBEDDING);
          truncatedText = text.substring(0, MAX_CHARS_PER_EMBEDDING);
        }
        Response<Embedding> response = embeddingModel.embed(truncatedText);
        float[] vector = response.content().vector();
        List<Float> embedding = new ArrayList<>(vector.length);
        for (float f : vector) {
          embedding.add(f);
        }
        results.add(embedding);
      }
      meterRegistry
          .counter("embedding.requests.success", "count", String.valueOf(texts.size()))
          .increment();
      return results;
    } finally {
      sample.stop(meterRegistry.timer("embedding.batch.duration"));
    }
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
