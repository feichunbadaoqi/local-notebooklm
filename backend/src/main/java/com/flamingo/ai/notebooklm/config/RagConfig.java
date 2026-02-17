package com.flamingo.ai.notebooklm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration properties for RAG pipeline. */
@Configuration
@ConfigurationProperties(prefix = "rag")
@Getter
@Setter
public class RagConfig {

  private Chunking chunking = new Chunking();
  private Retrieval retrieval = new Retrieval();
  private Compaction compaction = new Compaction();
  private Memory memory = new Memory();
  private Metadata metadata = new Metadata();
  private Diversity diversity = new Diversity();
  private QueryReformulation queryReformulation = new QueryReformulation();
  private Reranking reranking = new Reranking();

  @Getter
  @Setter
  public static class Chunking {
    private int size = 512;
    private int overlap = 50;
  }

  @Getter
  @Setter
  public static class Retrieval {
    private int topK = 6;
    private int rrfK = 60;
    private int candidatesMultiplier = 2;
  }

  @Getter
  @Setter
  public static class Compaction {
    private int slidingWindowSize = 10;
    private int tokenThreshold = 3000;
    private int messageThreshold = 30;
    private int batchSize = 20;
  }

  @Getter
  @Setter
  public static class Memory {
    private boolean enabled = true;
    private int maxPerSession = 50;
    private float extractionThreshold = 0.3f;
    private int contextLimit = 5;
    private Float semanticWeight = 0.7f; // 70% semantic relevance, 30% importance
    private Integer candidatePoolMultiplier = 3; // Fetch 3x candidates for reranking
  }

  @Getter
  @Setter
  public static class Metadata {
    private boolean extractKeywords = true;
    private int maxKeywords = 10;
    private boolean extractSections = true;
    private boolean enrichChunks = true;
  }

  @Getter
  @Setter
  public static class Diversity {
    private boolean enabled = true;
    private int minChunksPerDocument = 2;
  }

  @Getter
  @Setter
  public static class QueryReformulation {
    private boolean enabled = true;
    private int historyWindow = 5;
    private Integer candidatePoolMultiplier = 4;
    private int maxQueryLength = 500;
  }

  @Getter
  @Setter
  public static class Reranking {
    private CrossEncoder crossEncoder = new CrossEncoder();
    private Llm llm = new Llm();

    @Getter
    @Setter
    public static class CrossEncoder {
      private boolean enabled = true;
      private String modelId = "elastic-rerank";
    }

    @Getter
    @Setter
    public static class Llm {
      private boolean enabled = false;
      private int batchSize = 20;
    }
  }
}
