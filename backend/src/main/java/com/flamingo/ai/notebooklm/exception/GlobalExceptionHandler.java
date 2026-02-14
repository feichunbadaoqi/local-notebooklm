package com.flamingo.ai.notebooklm.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Global exception handler for REST controllers. */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  private final MeterRegistry meterRegistry;

  @ExceptionHandler(SessionNotFoundException.class)
  public ResponseEntity<ApiError> handleSessionNotFound(
      SessionNotFoundException ex, HttpServletRequest request) {

    incrementErrorCounter("session_not_found");
    String errorId = generateErrorId();
    log.warn("Session not found [{}]: {}", errorId, ex.getSessionId());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            ApiError.builder()
                .errorId(errorId)
                .code(ApiError.SESSION_NOT_FOUND)
                .message("Session not found")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
  }

  @ExceptionHandler(DocumentNotFoundException.class)
  public ResponseEntity<ApiError> handleDocumentNotFound(
      DocumentNotFoundException ex, HttpServletRequest request) {

    incrementErrorCounter("document_not_found");
    String errorId = generateErrorId();
    log.warn("Document not found [{}]: {}", errorId, ex.getDocumentId());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            ApiError.builder()
                .errorId(errorId)
                .code(ApiError.DOCUMENT_NOT_FOUND)
                .message("Document not found")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
  }

  @ExceptionHandler(MemoryNotFoundException.class)
  public ResponseEntity<ApiError> handleMemoryNotFound(
      MemoryNotFoundException ex, HttpServletRequest request) {

    incrementErrorCounter("memory_not_found");
    String errorId = generateErrorId();
    log.warn("Memory not found [{}]: {}", errorId, ex.getMemoryId());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            ApiError.builder()
                .errorId(errorId)
                .code(ApiError.MEMORY_NOT_FOUND)
                .message("Memory not found")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
  }

  @ExceptionHandler(MemoryAccessDeniedException.class)
  public ResponseEntity<ApiError> handleMemoryAccessDenied(
      MemoryAccessDeniedException ex, HttpServletRequest request) {

    incrementErrorCounter("memory_access_denied");
    String errorId = generateErrorId();
    log.warn(
        "Memory access denied [{}]: memory={}, session={}",
        errorId,
        ex.getMemoryId(),
        ex.getSessionId());

    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            ApiError.builder()
                .errorId(errorId)
                .code(ApiError.MEMORY_ACCESS_DENIED)
                .message("Access to this memory is denied")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
  }

  @ExceptionHandler(DocumentProcessingException.class)
  public ResponseEntity<ApiError> handleDocumentProcessing(
      DocumentProcessingException ex, HttpServletRequest request) {

    incrementErrorCounter("document_processing");
    String errorId = generateErrorId();
    log.error("Document processing error [{}]: {}", errorId, ex.getMessage(), ex);

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(
            ApiError.builder()
                .errorId(errorId)
                .code(ApiError.DOCUMENT_PROCESSING_ERROR)
                .message(ex.getUserMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
  }

  @ExceptionHandler(LlmServiceException.class)
  public ResponseEntity<ApiError> handleLlmService(
      LlmServiceException ex, HttpServletRequest request) {

    String errorType = ex.isRateLimited() ? "llm_rate_limited" : "llm_error";
    incrementErrorCounter(errorType);
    String errorId = generateErrorId();
    log.error("LLM service error [{}]: {}", errorId, ex.getMessage(), ex);

    String code = ex.isRateLimited() ? ApiError.LLM_RATE_LIMITED : ApiError.LLM_UNAVAILABLE;

    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiError.builder()
                .errorId(errorId)
                .code(code)
                .message(ex.getUserMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
  }

  @ExceptionHandler(SearchException.class)
  public ResponseEntity<ApiError> handleSearch(SearchException ex, HttpServletRequest request) {

    incrementErrorCounter("search_error");
    String errorId = generateErrorId();
    log.error("Search error [{}]: {}", errorId, ex.getMessage(), ex);

    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiError.builder()
                .errorId(errorId)
                .code(ApiError.SEARCH_FAILED)
                .message(ex.getUserMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {

    incrementErrorCounter("validation_error");
    String errorId = generateErrorId();

    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("Validation failed");

    log.warn("Validation error [{}]: {}", errorId, message);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ApiError.builder()
                .errorId(errorId)
                .code(ApiError.VALIDATION_ERROR)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {

    incrementErrorCounter("internal_error");
    String errorId = generateErrorId();
    log.error("Unexpected error [{}]: {}", errorId, ex.getMessage(), ex);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ApiError.builder()
                .errorId(errorId)
                .code(ApiError.INTERNAL_ERROR)
                .message("An unexpected error occurred. Please try again later.")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build());
  }

  private void incrementErrorCounter(String errorType) {
    meterRegistry.counter("api_errors_total", "error_type", errorType).increment();
  }

  private String generateErrorId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
