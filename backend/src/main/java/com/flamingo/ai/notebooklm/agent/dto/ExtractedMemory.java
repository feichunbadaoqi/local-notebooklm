package com.flamingo.ai.notebooklm.agent.dto;

/**
 * Structured output from MemoryExtractionAgent. Represents an extracted memory from a conversation
 * exchange.
 */
public record ExtractedMemory(
    String type, // "fact", "preference", "insight"
    String content, // Memory text
    float importance // 0.0 to 1.0
    ) {}
