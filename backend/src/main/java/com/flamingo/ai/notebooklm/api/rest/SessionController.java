package com.flamingo.ai.notebooklm.api.rest;

import com.flamingo.ai.notebooklm.api.dto.request.CreateSessionRequest;
import com.flamingo.ai.notebooklm.api.dto.request.UpdateSessionRequest;
import com.flamingo.ai.notebooklm.api.dto.response.SessionResponse;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for session management. */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

  private final SessionService sessionService;
  private final DocumentRepository documentRepository;
  private final ChatMessageRepository chatMessageRepository;

  /** Creates a new session. */
  @PostMapping
  public ResponseEntity<SessionResponse> createSession(
      @Valid @RequestBody CreateSessionRequest request) {
    Session session = sessionService.createSession(request);
    // New session has 0 documents and 0 messages
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(SessionResponse.fromEntity(session, 0, 0));
  }

  /** Gets all sessions. */
  @GetMapping
  public ResponseEntity<List<SessionResponse>> getAllSessions() {
    List<Session> sessions = sessionService.getAllSessions();
    List<SessionResponse> responses =
        sessions.stream()
            .map(
                session ->
                    SessionResponse.fromEntity(
                        session,
                        documentRepository.countBySessionId(session.getId()),
                        chatMessageRepository.countBySessionId(session.getId())))
            .toList();
    return ResponseEntity.ok(responses);
  }

  /** Gets a session by ID. */
  @GetMapping("/{sessionId}")
  public ResponseEntity<SessionResponse> getSession(@PathVariable UUID sessionId) {
    Session session = sessionService.getSession(sessionId);
    sessionService.touchSession(sessionId);
    long documentCount = documentRepository.countBySessionId(sessionId);
    long messageCount = chatMessageRepository.countBySessionId(sessionId);
    return ResponseEntity.ok(SessionResponse.fromEntity(session, documentCount, messageCount));
  }

  /** Updates a session. */
  @PutMapping("/{sessionId}")
  public ResponseEntity<SessionResponse> updateSession(
      @PathVariable UUID sessionId, @Valid @RequestBody UpdateSessionRequest request) {
    Session session = sessionService.updateSession(sessionId, request);
    long documentCount = documentRepository.countBySessionId(sessionId);
    long messageCount = chatMessageRepository.countBySessionId(sessionId);
    return ResponseEntity.ok(SessionResponse.fromEntity(session, documentCount, messageCount));
  }

  /** Updates the interaction mode of a session. */
  @PutMapping("/{sessionId}/mode")
  public ResponseEntity<SessionResponse> updateSessionMode(
      @PathVariable UUID sessionId, @RequestBody InteractionMode mode) {
    Session session = sessionService.updateSessionMode(sessionId, mode);
    long documentCount = documentRepository.countBySessionId(sessionId);
    long messageCount = chatMessageRepository.countBySessionId(sessionId);
    return ResponseEntity.ok(SessionResponse.fromEntity(session, documentCount, messageCount));
  }

  /** Deletes a session. */
  @DeleteMapping("/{sessionId}")
  public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
    sessionService.deleteSession(sessionId);
    return ResponseEntity.noContent().build();
  }
}
