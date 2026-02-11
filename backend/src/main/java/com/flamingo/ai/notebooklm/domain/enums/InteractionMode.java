package com.flamingo.ai.notebooklm.domain.enums;

/** Defines the interaction modes for chat sessions. */
public enum InteractionMode {
  /** Broad discovery mode - encourages tangential exploration and suggestions. */
  EXPLORING(8, "Broad discovery, tangential suggestions"),

  /** Research mode - precise citations and fact-focused responses. */
  RESEARCH(4, "Precise citations, fact-focused"),

  /** Learning mode - Socratic method, builds understanding progressively. */
  LEARNING(6, "Socratic method, builds understanding");

  private final int retrievalCount;
  private final String description;

  InteractionMode(int retrievalCount, String description) {
    this.retrievalCount = retrievalCount;
    this.description = description;
  }

  public int getRetrievalCount() {
    return retrievalCount;
  }

  public String getDescription() {
    return description;
  }
}
