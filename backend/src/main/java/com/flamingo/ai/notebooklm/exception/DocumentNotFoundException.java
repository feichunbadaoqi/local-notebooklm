package com.flamingo.ai.notebooklm.exception;

import java.util.UUID;

/** Exception thrown when a document is not found. */
public class DocumentNotFoundException extends RuntimeException {

  private final UUID documentId;

  public DocumentNotFoundException(UUID documentId) {
    super("Document not found: " + documentId);
    this.documentId = documentId;
  }

  public UUID getDocumentId() {
    return documentId;
  }
}
