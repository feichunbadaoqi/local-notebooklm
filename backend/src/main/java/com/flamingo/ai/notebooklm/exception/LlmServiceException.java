package com.flamingo.ai.notebooklm.exception;

/** Exception thrown when LLM service fails. */
public class LlmServiceException extends RuntimeException {

  private final boolean rateLimited;
  private final String userMessage;

  public LlmServiceException(String message) {
    super(message);
    this.rateLimited = false;
    this.userMessage = "AI service is temporarily unavailable. Please try again later.";
  }

  public LlmServiceException(String message, Throwable cause) {
    super(message, cause);
    this.rateLimited = false;
    this.userMessage = "AI service is temporarily unavailable. Please try again later.";
  }

  public LlmServiceException(String message, boolean rateLimited) {
    super(message);
    this.rateLimited = rateLimited;
    this.userMessage =
        rateLimited
            ? "Service is temporarily busy. Please try again in a moment."
            : "AI service is temporarily unavailable. Please try again later.";
  }

  public boolean isRateLimited() {
    return rateLimited;
  }

  public String getUserMessage() {
    return userMessage;
  }
}
