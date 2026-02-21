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
  private Diversity diversity = new Diversity();
  private QueryReformulation queryReformulation = new QueryReformulation();
  private Reranking reranking = new Reranking();
  private ImageStorage imageStorage = new ImageStorage();
  private ImageGrouping imageGrouping = new ImageGrouping();
  private ContextualChunking contextualChunking = new ContextualChunking();

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
    /**
     * Reranking strategy: "tei" (default, cross-encoder via TEI) or "llm" (OpenAI prompt-based).
     */
    private String strategy = "tei";

    private Tei tei = new Tei();
    private Llm llm = new Llm();

    /** Configuration for TEI (Text Embeddings Inference) cross-encoder reranker. */
    @Getter
    @Setter
    public static class Tei {
      private String baseUrl = "http://localhost:8090";
      private String modelId = "cross-encoder/ms-marco-MiniLM-L6-v2";
      private boolean truncate = true;
      private boolean rawScores = false;
      private int connectTimeoutMs = 5000;
      private int readTimeoutMs = 10000;
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

    /**
     * Whether to render composite images for spatial groups.
     *
     * <p>When true, multiple small images that are spatially close (e.g., icons in a diagram) are
     * rendered as a single composite image from the PDF, matching what users see in the original
     * document. When false, each extracted image is stored individually.
     */
    private boolean compositeRenderingEnabled = true;
  }

  /** Configuration for image grouping strategies to keep related images together in chunks. */
  @Getter
  @Setter
  public static class ImageGrouping {
    /** Strategy to use: "spatial" (default) or "page-based". */
    private String strategy = "spatial";

    private Spatial spatial = new Spatial();
    private PageBased pageBased = new PageBased();

    /** Configuration for spatial clustering strategy. */
    @Getter
    @Setter
    public static class Spatial {
      /** Distance threshold in PDF coordinate units (~72 units per inch). */
      private float threshold = 100.0f;

      /** Minimum number of images to form a group. */
      private int minGroupSize = 2;
    }

    /** Configuration for page-based grouping strategy. */
    @Getter
    @Setter
    public static class PageBased {
      /** Minimum number of images on a page to form a group. */
      private int minGroupSize = 2;
    }
  }

  /**
   * Configuration for contextual chunking (Anthropic's technique). When enabled, generates a 1-2
   * sentence prefix for each chunk situating it within the overall document context.
   */
  @Getter
  @Setter
  public static class ContextualChunking {
    /** Whether contextual chunking is enabled. Disabled by default. */
    private boolean enabled = false;

    /** Maximum characters of the document summary to include as context for the LLM. */
    private int maxSummaryChars = 2000;
  }
}
