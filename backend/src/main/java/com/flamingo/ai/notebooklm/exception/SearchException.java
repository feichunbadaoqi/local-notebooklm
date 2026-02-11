package com.flamingo.ai.notebooklm.exception;

/** Exception thrown when search operations fail. */
public class SearchException extends RuntimeException {

  private final String userMessage;

  public SearchException(String message) {
    super(message);
    this.userMessage = "Search is temporarily unavailable. Please try again.";
  }

  public SearchException(String message, Throwable cause) {
    super(message, cause);
    this.userMessage = "Search is temporarily unavailable. Please try again.";
  }

  public String getUserMessage() {
    return userMessage;
  }
}
