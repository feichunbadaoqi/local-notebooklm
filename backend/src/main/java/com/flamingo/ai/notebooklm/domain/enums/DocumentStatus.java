package com.flamingo.ai.notebooklm.domain.enums;

/** Defines the processing status of an uploaded document. */
public enum DocumentStatus {
  /** Document has been uploaded but not yet processed. */
  PENDING,

  /** Document is currently being processed (parsing, chunking, embedding). */
  PROCESSING,

  /** Document has been successfully processed and is ready for queries. */
  READY,

  /** Document processing failed. */
  FAILED
}
