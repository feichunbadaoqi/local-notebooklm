package com.flamingo.ai.notebooklm.service.rag;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Metrics for RAG evaluation (Stage 3).
 *
 * <p>Tracks retrieval quality across multiple test cases: - Recall@K: % of expected chunks found in
 * top K - MRR: Mean Reciprocal Rank of first relevant result - nDCG@K: Normalized Discounted
 * Cumulative Gain - Answer Accuracy: % answers with required keywords - Citation Correctness: %
 * citations pointing to relevant chunks
 */
@Data
public class RagMetrics {

  private List<Double> recallAtKValues = new ArrayList<>();
  private List<Double> mrrValues = new ArrayList<>();
  private List<Double> ndcgAtKValues = new ArrayList<>();
  private List<Double> answerAccuracyValues = new ArrayList<>();
  private List<Double> citationCorrectnessValues = new ArrayList<>();

  private int totalTestCases = 0;

  /** Adds a recall@K measurement. */
  public void addRecall(double recall) {
    recallAtKValues.add(recall);
  }

  /** Adds an MRR measurement. */
  public void addMRR(double mrr) {
    mrrValues.add(mrr);
  }

  /** Adds an nDCG@K measurement. */
  public void addNdcg(double ndcg) {
    ndcgAtKValues.add(ndcg);
  }

  /** Adds an answer accuracy measurement. */
  public void addAnswerAccuracy(double accuracy) {
    answerAccuracyValues.add(accuracy);
  }

  /** Adds a citation correctness measurement. */
  public void addCitationCorrectness(double correctness) {
    citationCorrectnessValues.add(correctness);
  }

  /** Increments total test cases. */
  public void incrementTestCases() {
    totalTestCases++;
  }

  /**
   * Calculates mean recall@K across all test cases.
   *
   * @return average recall@K (0.0-1.0)
   */
  public double getRecallAtK() {
    if (recallAtKValues.isEmpty()) {
      return 0.0;
    }
    return recallAtKValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  /**
   * Calculates mean MRR across all test cases.
   *
   * @return average MRR (0.0-1.0)
   */
  public double getMRR() {
    if (mrrValues.isEmpty()) {
      return 0.0;
    }
    return mrrValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  /**
   * Calculates mean nDCG@K across all test cases.
   *
   * @return average nDCG@K (0.0-1.0)
   */
  public double getNdcgAtK() {
    if (ndcgAtKValues.isEmpty()) {
      return 0.0;
    }
    return ndcgAtKValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  /**
   * Calculates mean answer accuracy across all test cases.
   *
   * @return average answer accuracy (0.0-1.0)
   */
  public double getAnswerAccuracy() {
    if (answerAccuracyValues.isEmpty()) {
      return 0.0;
    }
    return answerAccuracyValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
  }

  /**
   * Calculates mean citation correctness across all test cases.
   *
   * @return average citation correctness (0.0-1.0)
   */
  public double getCitationCorrectness() {
    if (citationCorrectnessValues.isEmpty()) {
      return 0.0;
    }
    return citationCorrectnessValues.stream()
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);
  }

  /**
   * Returns a formatted summary of all metrics.
   *
   * @return multi-line string with metric summary
   */
  public String getSummary() {
    return String.format(
        """
            RAG Evaluation Metrics (n=%d test cases):
            ==========================================
            Recall@5:              %.3f (target: ≥0.70)
            MRR:                   %.3f (target: ≥0.60)
            nDCG@5:                %.3f (target: ≥0.75)
            Answer Accuracy:       %.3f (target: ≥0.80)
            Citation Correctness:  %.3f (target: ≥0.90)
            """,
        totalTestCases,
        getRecallAtK(),
        getMRR(),
        getNdcgAtK(),
        getAnswerAccuracy(),
        getCitationCorrectness());
  }
}
