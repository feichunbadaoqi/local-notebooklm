package com.flamingo.ai.notebooklm.agent.dto;

import java.util.List;

/**
 * Structured output from DocumentAnalysisAgent. Contains both a summary and extracted topics from a
 * single LLM call.
 */
public record DocumentAnalysisResult(
    String summary, // 800-1000 word document summary
    List<String> topics // 5-15 key topics/concepts (20-40 words each)
    ) {}
