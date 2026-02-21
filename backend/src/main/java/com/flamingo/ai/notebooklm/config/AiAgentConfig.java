package com.flamingo.ai.notebooklm.config;

import com.flamingo.ai.notebooklm.agent.ChatCompactionAgent;
import com.flamingo.ai.notebooklm.agent.ChatStreamingAgent;
import com.flamingo.ai.notebooklm.agent.CrossEncoderRerankerAgent;
import com.flamingo.ai.notebooklm.agent.MemoryExtractionAgent;
import com.flamingo.ai.notebooklm.agent.QueryReformulationAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for reusable AI agents using LangChain4j AI Services.
 *
 * <p>Pattern: Define agent interfaces with @SystemMessage/@UserMessage, build concrete
 * implementations using AiServices.builder().
 */
@Configuration
public class AiAgentConfig {

  /**
   * Query reformulation agent for conversational RAG. Uses ChatLanguageModel for fast,
   * non-streaming structured output.
   */
  @Bean
  public QueryReformulationAgent queryReformulationAgent(ChatModel chatModel) {
    return AiServices.builder(QueryReformulationAgent.class).chatModel(chatModel).build();
  }

  /**
   * Memory extraction agent for extracting important information from conversations. Uses ChatModel
   * for structured output.
   */
  @Bean
  public MemoryExtractionAgent memoryExtractionAgent(ChatModel chatModel) {
    return AiServices.builder(MemoryExtractionAgent.class).chatModel(chatModel).build();
  }

  /**
   * Cross-encoder reranker agent for scoring passage relevance. Uses ChatModel for semantic scoring
   * of query-passage pairs.
   */
  @Bean
  public CrossEncoderRerankerAgent crossEncoderRerankerAgent(ChatModel chatModel) {
    return AiServices.builder(CrossEncoderRerankerAgent.class).chatModel(chatModel).build();
  }

  /**
   * Chat compaction agent for summarizing conversation history. Uses ChatModel to generate concise
   * summaries preserving key facts.
   */
  @Bean
  public ChatCompactionAgent chatCompactionAgent(ChatModel chatModel) {
    return AiServices.builder(ChatCompactionAgent.class).chatModel(chatModel).build();
  }

  /**
   * Chat streaming agent for streaming RAG-enhanced chat responses. Uses StreamingChatModel for
   * reactive token-by-token streaming.
   */
  @Bean
  public ChatStreamingAgent chatStreamingAgent(StreamingChatModel streamingChatModel) {
    return AiServices.builder(ChatStreamingAgent.class)
        .streamingChatModel(streamingChatModel)
        .build();
  }
}
