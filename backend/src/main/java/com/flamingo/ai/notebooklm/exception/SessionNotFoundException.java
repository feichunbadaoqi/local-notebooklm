package com.flamingo.ai.notebooklm.exception;

import java.util.UUID;

/** Exception thrown when a session is not found. */
public class SessionNotFoundException extends RuntimeException {

  private final UUID sessionId;

  public SessionNotFoundException(UUID sessionId) {
    super("Session not found: " + sessionId);
    this.sessionId = sessionId;
  }

  public UUID getSessionId() {
    return sessionId;
  }
}
