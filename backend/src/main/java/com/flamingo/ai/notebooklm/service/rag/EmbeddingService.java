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

  private static final int MAX_TOKENS_PER_EMBEDDING = 8000; // Leave margin below 8192 limit

  private final EmbeddingModel embeddingModel;
  private final MeterRegistry meterRegistry;

  @CircuitBreaker(name = "openai", fallbackMethod = "embedTextFallback")
  @Retry(name = "openai")
  public List<Float> embedText(String text) {
    log.debug("embedText called, input length: {} chars", text.length());
    // Truncate if too long (roughly 4 chars per token)
    if (text.length() > MAX_TOKENS_PER_EMBEDDING * 4) {
      log.warn("Text too long for embedding, truncating from {} chars", text.length());
      text = text.substring(0, MAX_TOKENS_PER_EMBEDDING * 4);
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
      for (String text : texts) {
        // Truncate if too long
        String truncatedText = text;
        if (text.length() > MAX_TOKENS_PER_EMBEDDING * 4) {
          log.warn("Chunk too long for embedding, truncating from {} chars", text.length());
          truncatedText = text.substring(0, MAX_TOKENS_PER_EMBEDDING * 4);
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
