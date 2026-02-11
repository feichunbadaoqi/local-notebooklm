package com.flamingo.ai.notebooklm.domain.enums;

/** Defines the role of a chat message sender. */
public enum MessageRole {
  /** Message from the user. */
  USER,

  /** Message from the AI assistant. */
  ASSISTANT,

  /** System message (instructions, context). */
  SYSTEM
}
