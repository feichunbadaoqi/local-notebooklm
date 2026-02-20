package com.flamingo.ai.notebooklm.agent.dto;

/**
 * Structured output from QueryReformulationAgent. LangChain4j will automatically deserialize LLM
 * JSON response to this record.
 */
public record QueryReformulationResult(
    boolean needsReformulation,
    /**
     * True when the query specifically continues the immediately preceding assistant response topic
     * (not just any prior context). Used downstream to activate source anchoring.
     */
    boolean isFollowUp,
    String query,
    String reasoning // Optional: why reformulation was/wasn't needed
    ) {}
