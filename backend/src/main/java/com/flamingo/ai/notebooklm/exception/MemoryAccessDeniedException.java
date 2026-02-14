package com.flamingo.ai.notebooklm.exception;

import java.util.UUID;

/** Exception thrown when attempting to access a memory that doesn't belong to the session. */
public class MemoryAccessDeniedException extends RuntimeException {

  private final UUID memoryId;
  private final UUID sessionId;

  public MemoryAccessDeniedException(UUID memoryId, UUID sessionId) {
    super(
        String.format(
            "Memory %s does not belong to session %s or access is denied", memoryId, sessionId));
    this.memoryId = memoryId;
    this.sessionId = sessionId;
  }

  public UUID getMemoryId() {
    return memoryId;
  }

  public UUID getSessionId() {
    return sessionId;
  }
}
