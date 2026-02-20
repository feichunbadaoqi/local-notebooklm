package com.flamingo.ai.notebooklm.agent.dto;

import java.util.List;

/**
 * Structured JSON output from CrossEncoderRerankerAgent. Contains relevance scores (0.0-1.0) for
 * each candidate passage in order.
 */
public record RerankingScores(List<Double> scores) {}
