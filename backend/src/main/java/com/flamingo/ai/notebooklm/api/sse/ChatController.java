package com.flamingo.ai.notebooklm.api.sse;

import com.flamingo.ai.notebooklm.api.dto.request.ChatRequest;
import com.flamingo.ai.notebooklm.api.dto.response.ChatMessageResponse;
import com.flamingo.ai.notebooklm.api.dto.response.StreamChunkResponse;
import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.service.chat.ChatCompactionService;
import com.flamingo.ai.notebooklm.service.chat.ChatService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** Controller for chat interactions with SSE streaming support. */
@RestController
@RequestMapping("/api/sessions/{sessionId}")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

  private final ChatService chatService;
  private final ChatCompactionService compactionService;
  private final MeterRegistry meterRegistry;

  private final AtomicInteger activeConnections = new AtomicInteger(0);

  /**
   * Streams a chat response using Server-Sent Events.
   *
   * @param sessionId the session ID
   * @param request the chat request containing the user message
   * @return a Flux of SSE events
   */
  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<StreamChunkResponse> streamChat(
      @PathVariable UUID sessionId, @Valid @RequestBody ChatRequest request) {

    log.info("Starting chat stream for session {}", sessionId);
    activeConnections.incrementAndGet();
    meterRegistry.gauge("sse.connections.active", activeConnections);

    return chatService
        .streamChat(sessionId, request.getMessage())
        .doOnComplete(
            () -> {
              activeConnections.decrementAndGet();
              log.debug("Chat stream completed for session {}", sessionId);
            })
        .doOnError(
            e -> {
              activeConnections.decrementAndGet();
              log.error("Chat stream error for session {}: {}", sessionId, e.getMessage());
              meterRegistry.counter("sse.errors").increment();
            })
        .doOnCancel(
            () -> {
              activeConnections.decrementAndGet();
              log.debug("Chat stream cancelled for session {}", sessionId);
            });
  }

  /**
   * Gets chat history for a session.
   *
   * @param sessionId the session ID
   * @param limit maximum number of messages (default 50)
   * @return list of chat messages
   */
  @GetMapping("/messages")
  public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
      @PathVariable UUID sessionId, @RequestParam(defaultValue = "50") int limit) {

    List<ChatMessage> messages = chatService.getChatHistory(sessionId, limit);
    List<ChatMessageResponse> response =
        messages.stream().map(ChatMessageResponse::fromEntity).toList();

    return ResponseEntity.ok(response);
  }

  /**
   * Forces compaction of chat history (ADVANCED).
   *
   * <p>Compaction is automatically triggered when chat history exceeds 30 messages or 3000 tokens.
   * Manual use of this endpoint is only needed for testing or forcing immediate compaction.
   *
   * <p><strong>Note:</strong> This is an advanced feature. Most users should not need to call this
   * endpoint as compaction happens automatically during normal chat flow.
   *
   * @param sessionId the session ID
   * @return 200 OK on success
   */
  @PostMapping("/compact")
  public ResponseEntity<Void> forceCompaction(@PathVariable UUID sessionId) {
    log.info("Force compaction requested for session {}", sessionId);
    compactionService.compact(sessionId);
    return ResponseEntity.ok().build();
  }
}
