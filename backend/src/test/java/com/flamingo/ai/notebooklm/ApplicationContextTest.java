package com.flamingo.ai.notebooklm;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.flamingo.ai.notebooklm.service.chat.ChatService;
import com.flamingo.ai.notebooklm.service.document.DocumentService;
import com.flamingo.ai.notebooklm.service.memory.MemoryService;
import com.flamingo.ai.notebooklm.service.rag.HybridSearchService;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test that verifies the Spring application context loads correctly. Uses @MockitoBean
 * to mock external dependencies (LLM, Elasticsearch) so the test can run without external services.
 */
@SpringBootTest
class ApplicationContextTest {

  // Mock external dependencies that require API keys or running services
  @MockitoBean private ChatModel chatModel;
  @MockitoBean private StreamingChatModel streamingChatModel;
  @MockitoBean private EmbeddingModel embeddingModel;
  @MockitoBean private ElasticsearchClient elasticsearchClient;

  @Autowired private ApplicationContext applicationContext;

  @Test
  @DisplayName("Application context should load successfully")
  void contextLoads() {
    assertThat(applicationContext).isNotNull();
  }

  @Test
  @DisplayName("All core service beans should be available")
  void coreServiceBeansShouldBeAvailable() {
    assertThat(applicationContext.getBean(SessionService.class)).isNotNull();
    assertThat(applicationContext.getBean(DocumentService.class)).isNotNull();
    assertThat(applicationContext.getBean(ChatService.class)).isNotNull();
    assertThat(applicationContext.getBean(MemoryService.class)).isNotNull();
    assertThat(applicationContext.getBean(HybridSearchService.class)).isNotNull();
  }
}
