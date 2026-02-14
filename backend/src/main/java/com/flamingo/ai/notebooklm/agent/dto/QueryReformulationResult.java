package com.flamingo.ai.notebooklm.agent.dto;

/**
 * Structured output from QueryReformulationAgent. LangChain4j will automatically deserialize LLM
 * JSON response to this record.
 */
public record QueryReformulationResult(
    boolean needsReformulation,
    String query,
    String reasoning // Optional: why reformulation was/wasn't needed
    ) {}
