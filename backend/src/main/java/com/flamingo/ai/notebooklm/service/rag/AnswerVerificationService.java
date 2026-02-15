package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import dev.langchain4j.model.chat.ChatModel;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Answer verification service (Stage 4) - verifies LLM-generated claims against evidence.
 *
 * <p>Prevents hallucinations by checking if each claim is supported by the cited passages. Uses LLM
 * to score claim-evidence support level (0.0-1.0).
 *
 * <p>Verification process: 1. Parse answer into claims/sentences 2. For each claim with citation,
 * verify it's supported by cited passage 3. Score support level using LLM (0.0 = not supported, 1.0
 * = fully supported) 4. Flag claims with score < threshold as unsupported
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnswerVerificationService {

  private final ChatModel chatModel;
  private final MeterRegistry meterRegistry;

  @Value("${rag.verification.support-threshold:0.7}")
  private double supportThreshold;

  @Value("${rag.verification.enabled:true}")
  private boolean enabled;

  /**
   * Verifies that LLM-generated answer claims are supported by evidence.
   *
   * @param answer the LLM-generated answer
   * @param evidence the retrieved document chunks
   * @return verification result with unsupported claims
   */
  @Timed(value = "rag.verification", description = "Time for answer verification")
  public VerificationResult verify(String answer, List<DocumentChunk> evidence) {
    if (!enabled) {
      log.debug("Answer verification disabled, skipping");
      return new VerificationResult(true, List.of());
    }

    if (answer == null || answer.isEmpty()) {
      return new VerificationResult(true, List.of());
    }

    if (evidence == null || evidence.isEmpty()) {
      log.warn("No evidence provided for verification");
      return new VerificationResult(true, List.of());
    }

    log.debug("Verifying answer with {} evidence chunks", evidence.size());

    // Extract claims with citations from answer
    List<ClaimWithCitation> claims = extractClaims(answer, evidence);
    log.debug("Extracted {} claims with citations", claims.size());

    if (claims.isEmpty()) {
      // No citations to verify
      return new VerificationResult(true, List.of());
    }

    // Verify each claim against its cited evidence
    List<UnsupportedClaim> unsupported = new ArrayList<>();

    for (ClaimWithCitation claim : claims) {
      double supportScore = scoreSupportLevel(claim.getText(), claim.getEvidence());

      if (supportScore < supportThreshold) {
        unsupported.add(
            new UnsupportedClaim(
                claim.getText(), claim.getCitationIndex(), supportScore, claim.getEvidence()));
        log.debug(
            "Claim unsupported (score={:.2f}): {}",
            supportScore,
            claim.getText().substring(0, Math.min(50, claim.getText().length())));
      }
    }

    boolean isValid = unsupported.isEmpty();

    // Track metrics
    meterRegistry.counter("rag.verification.total").increment();
    if (!isValid) {
      meterRegistry.counter("rag.verification.unsupported_claims").increment(unsupported.size());
    }

    log.debug(
        "Verification complete: {} claims verified, {} unsupported",
        claims.size(),
        unsupported.size());

    return new VerificationResult(isValid, unsupported);
  }

  /**
   * Extracts claims with citations from the answer.
   *
   * @param answer the LLM answer
   * @param evidence available evidence chunks
   * @return list of claims with their citations
   */
  private List<ClaimWithCitation> extractClaims(String answer, List<DocumentChunk> evidence) {
    List<ClaimWithCitation> claims = new ArrayList<>();

    // Pattern to match citations: [Source N], [N], (Source N), etc.
    // Captures both "Source N" and just "N" patterns
    Pattern citationPattern =
        Pattern.compile("\\[(?:Source\\s+)?(\\d+)\\]|\\((?:Source\\s+)?(\\d+)\\)");

    // Split answer into sentences (simple heuristic)
    String[] sentences = answer.split("(?<=[.!?])\\s+");

    for (String sentence : sentences) {
      Matcher matcher = citationPattern.matcher(sentence);
      if (matcher.find()) {
        // Extract citation number (group 1 or group 2, one of them will match)
        String citationNum = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        int citationIndex = Integer.parseInt(citationNum) - 1; // Convert to 0-indexed

        if (citationIndex >= 0 && citationIndex < evidence.size()) {
          claims.add(
              new ClaimWithCitation(
                  sentence.trim(), citationIndex, evidence.get(citationIndex).getContent()));
        }
      }
    }

    return claims;
  }

  /**
   * Scores how well the evidence supports the claim using LLM.
   *
   * @param claim the claim to verify
   * @param evidence the evidence text
   * @return support score (0.0 = not supported, 1.0 = fully supported)
   */
  private double scoreSupportLevel(String claim, String evidence) {
    String prompt =
        String.format(
            """
                Does the evidence fully support the claim? Answer with a score from 0.0 to 1.0.

                Scoring:
                - 1.0: Evidence completely supports the claim with direct statements
                - 0.7-0.9: Evidence strongly supports the claim with clear implications
                - 0.4-0.6: Evidence partially supports the claim
                - 0.1-0.3: Evidence weakly relates to the claim
                - 0.0: Evidence does not support or contradicts the claim

                Claim: %s

                Evidence: %s

                Return ONLY the numeric score (e.g., 0.8). Do not include explanations.
                Score: """,
            claim, evidence.substring(0, Math.min(1000, evidence.length())));

    try {
      String response = chatModel.chat(prompt);
      // Extract first number from response
      String cleaned = response.trim().replaceAll("[^0-9.]", "");
      double score = Double.parseDouble(cleaned);
      // Clamp to [0.0, 1.0]
      return Math.max(0.0, Math.min(1.0, score));
    } catch (Exception e) {
      log.warn("Failed to score support level: {}, defaulting to 0.5", e.getMessage());
      return 0.5; // Default uncertain score
    }
  }

  /** Claim with citation extracted from answer. */
  @Data
  @AllArgsConstructor
  private static class ClaimWithCitation {
    private String text;
    private int citationIndex;
    private String evidence;
  }

  /** Unsupported claim with score. */
  @Data
  @AllArgsConstructor
  public static class UnsupportedClaim {
    private String claim;
    private int citationIndex;
    private double supportScore;
    private String evidence;
  }

  /** Verification result. */
  @Data
  @AllArgsConstructor
  public static class VerificationResult {
    private boolean isValid;
    private List<UnsupportedClaim> unsupportedClaims;
  }
}
