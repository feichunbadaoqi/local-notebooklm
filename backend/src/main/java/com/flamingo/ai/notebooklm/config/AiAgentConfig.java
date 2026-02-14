package com.flamingo.ai.notebooklm.config;

import com.flamingo.ai.notebooklm.agent.MemoryExtractionAgent;
import com.flamingo.ai.notebooklm.agent.QueryReformulationAgent;
import dev.langchain4j.model.chat.ChatModel;
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
}
