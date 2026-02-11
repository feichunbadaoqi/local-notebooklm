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

  private final EmbeddingModel embeddingModel;
  private final MeterRegistry meterRegistry;

  @CircuitBreaker(name = "openai", fallbackMethod = "embedTextFallback")
  @Retry(name = "openai")
  public List<Float> embedText(String text) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      Response<Embedding> response = embeddingModel.embed(text);
      meterRegistry.counter("embedding.requests.success").increment();

      float[] vector = response.content().vector();
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
      // Batch embeddings - LangChain4j handles batching internally
      for (String text : texts) {
        Response<Embedding> response = embeddingModel.embed(text);
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
