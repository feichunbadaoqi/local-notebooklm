package com.flamingo.ai.notebooklm.exception;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/** Structured API error response. */
@Getter
@Builder
public class ApiError {

  // Error codes
  public static final String SESSION_NOT_FOUND = "SESSION_001";
  public static final String MEMORY_NOT_FOUND = "MEMORY_001";
  public static final String MEMORY_ACCESS_DENIED = "MEMORY_002";
  public static final String DOCUMENT_NOT_FOUND = "DOCUMENT_001";
  public static final String DOCUMENT_PARSE_ERROR = "DOCUMENT_002";
  public static final String DOCUMENT_PROCESSING_ERROR = "DOCUMENT_003";
  public static final String LLM_UNAVAILABLE = "LLM_001";
  public static final String LLM_RATE_LIMITED = "LLM_002";
  public static final String LLM_ERROR = "LLM_003";
  public static final String SEARCH_FAILED = "SEARCH_001";
  public static final String ELASTICSEARCH_ERROR = "SEARCH_002";
  public static final String VALIDATION_ERROR = "VALIDATION_001";
  public static final String INTERNAL_ERROR = "INTERNAL_001";

  /** Unique error ID for log correlation. */
  private final String errorId;

  /** Machine-readable error code. */
  private final String code;

  /** User-friendly error message. */
  private final String message;

  /** Technical details (only in dev mode). */
  private final String details;

  /** Timestamp of the error. */
  private final Instant timestamp;

  /** Request path that caused the error. */
  private final String path;
}
