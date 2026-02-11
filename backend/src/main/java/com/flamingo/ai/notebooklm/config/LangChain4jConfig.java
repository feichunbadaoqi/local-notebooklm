package com.flamingo.ai.notebooklm.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for LangChain4j models. */
@Configuration
public class LangChain4jConfig {

  @Value("${langchain4j.openai.api-key:}")
  private String openAiApiKey;

  @Value("${langchain4j.openai.chat-model.model-name:gpt-4o-mini}")
  private String chatModelName;

  @Value("${langchain4j.openai.chat-model.temperature:0.7}")
  private double temperature;

  @Value("${langchain4j.openai.chat-model.max-tokens:2048}")
  private int maxTokens;

  @Value("${langchain4j.openai.embedding-model.model-name:text-embedding-3-small}")
  private String embeddingModelName;

  @Value("${langchain4j.openai.embedding-model.dimensions:1536}")
  private int embeddingDimensions;

  @Bean
  public ChatLanguageModel chatLanguageModel() {
    if (openAiApiKey == null || openAiApiKey.isBlank()) {
      throw new IllegalStateException(
          "OpenAI API key is required. Set OPENAI_API_KEY environment variable.");
    }

    return OpenAiChatModel.builder()
        .apiKey(openAiApiKey)
        .modelName(chatModelName)
        .temperature(temperature)
        .maxTokens(maxTokens)
        .timeout(Duration.ofSeconds(60))
        .logRequests(false)
        .logResponses(false)
        .build();
  }

  @Bean
  public EmbeddingModel embeddingModel() {
    if (openAiApiKey == null || openAiApiKey.isBlank()) {
      throw new IllegalStateException(
          "OpenAI API key is required. Set OPENAI_API_KEY environment variable.");
    }

    return OpenAiEmbeddingModel.builder()
        .apiKey(openAiApiKey)
        .modelName(embeddingModelName)
        .dimensions(embeddingDimensions)
        .timeout(Duration.ofSeconds(30))
        .build();
  }
}
