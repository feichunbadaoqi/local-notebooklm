package com.flamingo.ai.notebooklm.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for LangChain4j models. */
@Configuration
public class LangChain4jConfig {

  @Value("${langchain4j.openai.api-key:}")
  private String openAiApiKey;

  @Value("${langchain4j.openai.chat-model.model-name:gpt-5-mini}")
  private String chatModelName;

  @Value("${langchain4j.openai.chat-model.max-completion-tokens:2048}")
  private int maxCompletionTokens;

  @Value("${langchain4j.openai.embedding-model.model-name:text-embedding-3-small}")
  private String embeddingModelName;

  @Value("${langchain4j.openai.embedding-model.dimensions:1536}")
  private int embeddingDimensions;

  @Bean
  public ChatModel chatModel() {
    validateApiKey();

    return OpenAiChatModel.builder()
        .apiKey(openAiApiKey)
        .modelName(chatModelName)
        .maxCompletionTokens(maxCompletionTokens)
        .timeout(Duration.ofSeconds(60))
        .responseFormat("json_object")
        .logRequests(false)
        .logResponses(false)
        .build();
  }

  @Bean
  public StreamingChatModel streamingChatModel() {
    validateApiKey();

    return OpenAiStreamingChatModel.builder()
        .apiKey(openAiApiKey)
        .modelName(chatModelName)
        .maxCompletionTokens(maxCompletionTokens)
        .timeout(Duration.ofSeconds(120))
        .logRequests(false)
        .logResponses(false)
        .build();
  }

  @Bean
  public EmbeddingModel embeddingModel() {
    validateApiKey();

    return OpenAiEmbeddingModel.builder()
        .apiKey(openAiApiKey)
        .modelName(embeddingModelName)
        .dimensions(embeddingDimensions)
        .timeout(Duration.ofSeconds(30))
        .build();
  }

  private void validateApiKey() {
    if (openAiApiKey == null || openAiApiKey.isBlank()) {
      throw new IllegalStateException(
          "OpenAI API key is required. Set OPENAI_API_KEY environment variable.");
    }
  }
}
