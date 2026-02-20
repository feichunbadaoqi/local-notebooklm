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
  private ImageStorage imageStorage = new ImageStorage();

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

    /** Enable additive RRF score boost for chunks from the previous response's documents. */
    private boolean sourceAnchoringEnabled = true;

    /** Additive boost applied to anchor document chunks in RRF scoring (conservative default). */
    private double sourceAnchoringBoost = 0.3;
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

    /** Minimum number of recent messages to always fetch from DB for recency-biased context. */
    private int minRecentMessages = 2;
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

  /** Configuration for image storage extracted during document processing. */
  @Getter
  @Setter
  public static class ImageStorage {
    /** Root directory for storing extracted images. */
    private String basePath = "data/images";

    /** Whether image extraction and storage is enabled. */
    private boolean enabled = true;

    /** Maximum image file size in bytes; images larger than this are skipped. */
    private long maxFileSizeBytes = 10 * 1024 * 1024L; // 10 MB
  }
}
