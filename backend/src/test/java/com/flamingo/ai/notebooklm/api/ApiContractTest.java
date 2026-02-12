package com.flamingo.ai.notebooklm.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flamingo.ai.notebooklm.api.rest.DocumentController;
import com.flamingo.ai.notebooklm.api.rest.MemoryController;
import com.flamingo.ai.notebooklm.api.rest.SessionController;
import com.flamingo.ai.notebooklm.api.sse.ChatController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contract tests to verify API endpoints match the design specification.
 *
 * <p>These tests ensure controllers are mapped to the correct paths as defined in CLAUDE.md:
 *
 * <ul>
 *   <li>POST /api/sessions - Create session
 *   <li>GET /api/sessions - List sessions
 *   <li>GET /api/sessions/{id} - Get session
 *   <li>PUT /api/sessions/{id}/mode - Change mode
 *   <li>DELETE /api/sessions/{id} - Delete session
 *   <li>POST /api/sessions/{id}/documents - Upload document
 *   <li>GET /api/sessions/{id}/messages - Get chat history
 *   <li>POST /api/sessions/{id}/chat/stream - Chat with streaming
 * </ul>
 *
 * <p>If these tests fail, it means the implementation has drifted from the design. Update the
 * controller mappings to match the specification.
 */
class ApiContractTest {

  @Nested
  @DisplayName("SessionController API contract")
  class SessionControllerContract {

    @Test
    @DisplayName("should be mapped to /api/sessions")
    void shouldBeMappedToApiSessions() {
      RequestMapping mapping = SessionController.class.getAnnotation(RequestMapping.class);
      assertThat(mapping).isNotNull();
      assertThat(mapping.value()).containsExactly("/api/sessions");
    }
  }

  @Nested
  @DisplayName("DocumentController API contract")
  class DocumentControllerContract {

    @Test
    @DisplayName("should be mapped under /api prefix")
    void shouldBeMappedUnderApiPrefix() {
      RequestMapping mapping = DocumentController.class.getAnnotation(RequestMapping.class);
      assertThat(mapping).isNotNull();
      assertThat(mapping.value()).containsExactly("/api");
    }
  }

  @Nested
  @DisplayName("ChatController API contract")
  class ChatControllerContract {

    @Test
    @DisplayName("should be mapped to /api/sessions/{sessionId}")
    void shouldBeMappedToApiSessionsWithId() {
      RequestMapping mapping = ChatController.class.getAnnotation(RequestMapping.class);
      assertThat(mapping).isNotNull();
      assertThat(mapping.value()).containsExactly("/api/sessions/{sessionId}");
    }
  }

  @Nested
  @DisplayName("MemoryController API contract")
  class MemoryControllerContract {

    @Test
    @DisplayName("should be mapped to /api/sessions/{sessionId}/memories")
    void shouldBeMappedToApiSessionsMemories() {
      RequestMapping mapping = MemoryController.class.getAnnotation(RequestMapping.class);
      assertThat(mapping).isNotNull();
      assertThat(mapping.value()).containsExactly("/api/sessions/{sessionId}/memories");
    }
  }
}
