package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * RAG Evaluation Test (Stage 3) - measures retrieval quality.
 *
 * <p>This test evaluates the RAG pipeline against a test set of queries and expected results.
 * Metrics tracked: - Recall@5: % of expected chunks found in top 5 - MRR: Mean Reciprocal Rank of
 * first relevant result - nDCG@5: Normalized Discounted Cumulative Gain
 *
 * <p>NOTE: This test is disabled by default because it requires: 1. Test documents uploaded to a
 * test session 2. Elasticsearch index populated with test data 3. Embedding model available for
 * queries
 *
 * <p>To enable: 1. Remove @Disabled annotation 2. Set up test data in setUp() method 3. Implement
 * actual document upload and indexing
 */
@SpringBootTest
@Slf4j
@DisplayName("RAG Evaluation Test")
class RagEvaluationTest {

  @Autowired private HybridSearchService hybridSearchService;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @Disabled("Requires test data setup - see class javadoc")
  @DisplayName("RAG Evaluation - Full Test Set")
  void evaluateRagPipeline() throws Exception {
    // Load test set
    JsonNode testSet = loadTestSet();
    JsonNode testCases = testSet.get("testCases");

    RagMetrics metrics = new RagMetrics();

    for (JsonNode testCase : testCases) {
      String id = testCase.get("id").asText();
      String query = testCase.get("query").asText();
      String difficulty = testCase.get("difficulty").asText();

      log.info("Evaluating test case: {} ({})", id, difficulty);

      // TODO: Implement actual search and evaluation
      // This requires:
      // 1. Upload and index test documents
      // 2. Run hybrid search with query
      // 3. Compare results to expected chunks
      // 4. Calculate metrics

      // Placeholder: Skip for now
      metrics.incrementTestCases();
    }

    // Log results
    log.info("\n{}", metrics.getSummary());

    // Assert baseline quality (currently skipped)
    // assertThat(metrics.getRecallAtK()).isGreaterThan(0.7);
    // assertThat(metrics.getMRR()).isGreaterThan(0.6);
    // assertThat(metrics.getNdcgAtK()).isGreaterThan(0.75);
  }

  @Test
  @Disabled("Requires test data setup")
  @DisplayName("RAG Ablation - Vector Only")
  void evaluateVectorOnly() {
    // TODO: Run evaluation with BM25 disabled
    // Compare metrics to full hybrid search
  }

  @Test
  @Disabled("Requires test data setup")
  @DisplayName("RAG Ablation - BM25 Only")
  void evaluateBm25Only() {
    // TODO: Run evaluation with vector search disabled
    // Compare metrics to full hybrid search
  }

  @Test
  @DisplayName("Should calculate recall@K correctly")
  void shouldCalculateRecallCorrectly() {
    RagMetrics metrics = new RagMetrics();

    // Test case 1: Found 2 out of 3 expected chunks
    metrics.addRecall(2.0 / 3.0); // 0.667

    // Test case 2: Found all expected chunks
    metrics.addRecall(1.0); // 1.0

    // Test case 3: Found 1 out of 2 expected chunks
    metrics.addRecall(0.5); // 0.5

    double avgRecall = metrics.getRecallAtK();
    assertThat(avgRecall).isBetween(0.72, 0.73); // (0.667 + 1.0 + 0.5) / 3 ≈ 0.722
  }

  @Test
  @DisplayName("Should calculate MRR correctly")
  void shouldCalculateMrrCorrectly() {
    RagMetrics metrics = new RagMetrics();

    // Test case 1: First relevant result at rank 1
    metrics.addMRR(1.0 / 1.0); // 1.0

    // Test case 2: First relevant result at rank 2
    metrics.addMRR(1.0 / 2.0); // 0.5

    // Test case 3: First relevant result at rank 3
    metrics.addMRR(1.0 / 3.0); // 0.333

    double avgMrr = metrics.getMRR();
    assertThat(avgMrr).isBetween(0.61, 0.62); // (1.0 + 0.5 + 0.333) / 3 ≈ 0.611
  }

  @Test
  @DisplayName("Should calculate nDCG@K correctly")
  void shouldCalculateNdcgCorrectly() {
    RagMetrics metrics = new RagMetrics();

    // Test case 1: Perfect ranking
    metrics.addNdcg(1.0);

    // Test case 2: Good ranking
    metrics.addNdcg(0.85);

    // Test case 3: Fair ranking
    metrics.addNdcg(0.70);

    double avgNdcg = metrics.getNdcgAtK();
    assertThat(avgNdcg).isBetween(0.84, 0.86); // (1.0 + 0.85 + 0.70) / 3 ≈ 0.85
  }

  @Test
  @DisplayName("Should format metrics summary correctly")
  void shouldFormatMetricsSummary() {
    RagMetrics metrics = new RagMetrics();
    metrics.addRecall(0.75);
    metrics.addMRR(0.65);
    metrics.addNdcg(0.80);
    metrics.addAnswerAccuracy(0.85);
    metrics.addCitationCorrectness(0.90);
    metrics.incrementTestCases();

    String summary = metrics.getSummary();

    assertThat(summary).contains("Recall@5:");
    assertThat(summary).contains("0.750");
    assertThat(summary).contains("MRR:");
    assertThat(summary).contains("0.650");
    assertThat(summary).contains("nDCG@5:");
    assertThat(summary).contains("0.800");
    assertThat(summary).contains("n=1 test cases");
  }

  /**
   * Loads the test set from resources.
   *
   * @return JSON test set
   */
  private JsonNode loadTestSet() throws IOException {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("rag-test-set.json")) {
      if (is == null) {
        throw new IOException("rag-test-set.json not found in resources");
      }
      return objectMapper.readTree(is);
    }
  }

  /**
   * Calculates recall for a test case.
   *
   * @param results search results
   * @param expectedChunks expected chunks with content patterns
   * @param topK number of results to consider
   * @return recall score (0.0-1.0)
   */
  private double calculateRecall(
      List<DocumentChunk> results, List<JsonNode> expectedChunks, int topK) {
    if (expectedChunks.isEmpty()) {
      return 1.0;
    }

    List<DocumentChunk> topResults = results.stream().limit(topK).toList();

    int found = 0;
    for (JsonNode expected : expectedChunks) {
      JsonNode contentMustContain = expected.get("contentMustContain");
      if (contentMustContain == null) {
        continue;
      }

      List<String> keywords = new ArrayList<>();
      contentMustContain.forEach(node -> keywords.add(node.asText().toLowerCase()));

      // Check if any result contains all required keywords
      boolean foundMatch =
          topResults.stream()
              .anyMatch(
                  chunk -> {
                    String content = chunk.getContent().toLowerCase();
                    return keywords.stream().allMatch(content::contains);
                  });

      if (foundMatch) {
        found++;
      }
    }

    return (double) found / expectedChunks.size();
  }

  /**
   * Finds the rank of the first relevant result.
   *
   * @param results search results
   * @param expectedChunks expected chunks
   * @return rank of first relevant result (1-indexed), or Integer.MAX_VALUE if none found
   */
  private int findFirstRelevantRank(List<DocumentChunk> results, List<JsonNode> expectedChunks) {
    for (int i = 0; i < results.size(); i++) {
      DocumentChunk result = results.get(i);
      String content = result.getContent().toLowerCase();

      for (JsonNode expected : expectedChunks) {
        JsonNode contentMustContain = expected.get("contentMustContain");
        if (contentMustContain == null) {
          continue;
        }

        List<String> keywords = new ArrayList<>();
        contentMustContain.forEach(node -> keywords.add(node.asText().toLowerCase()));

        if (keywords.stream().allMatch(content::contains)) {
          return i + 1; // Rank is 1-indexed
        }
      }
    }

    return Integer.MAX_VALUE; // No relevant result found
  }
}
