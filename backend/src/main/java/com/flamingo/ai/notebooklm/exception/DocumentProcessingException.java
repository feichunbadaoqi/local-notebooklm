package com.flamingo.ai.notebooklm.exception;

import java.util.UUID;

/** Exception thrown when document processing fails. */
public class DocumentProcessingException extends RuntimeException {

  private final UUID documentId;
  private final String userMessage;

  public DocumentProcessingException(UUID documentId, String message) {
    super(message);
    this.documentId = documentId;
    this.userMessage = "Failed to process document";
  }

  public DocumentProcessingException(UUID documentId, String message, Throwable cause) {
    super(message, cause);
    this.documentId = documentId;
    this.userMessage = "Failed to process document";
  }

  public DocumentProcessingException(UUID documentId, String message, String userMessage) {
    super(message);
    this.documentId = documentId;
    this.userMessage = userMessage;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public String getUserMessage() {
    return userMessage;
  }
}
