package com.flamingo.ai.notebooklm.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI agent for verifying claims against evidence.
 *
 * <p>Scores how well evidence supports a claim to prevent hallucinations in LLM-generated answers.
 */
public interface AnswerVerificationAgent {

  @SystemMessage(
      """
        You are a fact-checking expert. Your task is to determine how well a piece of
        evidence supports a given claim.

        Scoring Guidelines:
        - 1.0: Evidence completely supports the claim with direct statements
        - 0.7-0.9: Evidence strongly supports the claim with clear implications
        - 0.4-0.6: Evidence partially supports the claim
        - 0.1-0.3: Evidence weakly relates to the claim
        - 0.0: Evidence does not support or contradicts the claim

        Return ONLY the numeric score (e.g., 0.8). Do not include explanations.
        """)
  @UserMessage(
      """
        Claim: {{claim}}

        Evidence: {{evidence}}

        Score:
        """)
  String scoreSupportLevel(@V("claim") String claim, @V("evidence") String evidence);
}
