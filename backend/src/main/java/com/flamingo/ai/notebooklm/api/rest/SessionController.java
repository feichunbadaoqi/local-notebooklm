package com.flamingo.ai.notebooklm.api.rest;

import com.flamingo.ai.notebooklm.api.dto.request.CreateSessionRequest;
import com.flamingo.ai.notebooklm.api.dto.request.UpdateSessionRequest;
import com.flamingo.ai.notebooklm.api.dto.response.SessionResponse;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
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

  /** Creates a new session. */
  @PostMapping
  public ResponseEntity<SessionResponse> createSession(
      @Valid @RequestBody CreateSessionRequest request) {
    Session session = sessionService.createSession(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.fromEntity(session));
  }

  /** Gets all sessions. */
  @GetMapping
  public ResponseEntity<List<SessionResponse>> getAllSessions() {
    List<Session> sessions = sessionService.getAllSessions();
    List<SessionResponse> responses = sessions.stream().map(SessionResponse::fromEntity).toList();
    return ResponseEntity.ok(responses);
  }

  /** Gets a session by ID. */
  @GetMapping("/{sessionId}")
  public ResponseEntity<SessionResponse> getSession(@PathVariable UUID sessionId) {
    Session session = sessionService.getSession(sessionId);
    sessionService.touchSession(sessionId);
    return ResponseEntity.ok(SessionResponse.fromEntity(session));
  }

  /** Updates a session. */
  @PutMapping("/{sessionId}")
  public ResponseEntity<SessionResponse> updateSession(
      @PathVariable UUID sessionId, @Valid @RequestBody UpdateSessionRequest request) {
    Session session = sessionService.updateSession(sessionId, request);
    return ResponseEntity.ok(SessionResponse.fromEntity(session));
  }

  /** Updates the interaction mode of a session. */
  @PutMapping("/{sessionId}/mode")
  public ResponseEntity<SessionResponse> updateSessionMode(
      @PathVariable UUID sessionId, @RequestBody InteractionMode mode) {
    Session session = sessionService.updateSessionMode(sessionId, mode);
    return ResponseEntity.ok(SessionResponse.fromEntity(session));
  }

  /** Deletes a session. */
  @DeleteMapping("/{sessionId}")
  public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
    sessionService.deleteSession(sessionId);
    return ResponseEntity.noContent().build();
  }
}
