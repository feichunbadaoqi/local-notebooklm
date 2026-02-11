package com.flamingo.ai.notebooklm.exception;

import java.util.UUID;

/** Exception thrown when a memory is not found. */
public class MemoryNotFoundException extends RuntimeException {

  private final UUID memoryId;

  public MemoryNotFoundException(UUID memoryId) {
    super("Memory not found with ID: " + memoryId);
    this.memoryId = memoryId;
  }

  public UUID getMemoryId() {
    return memoryId;
  }
}
