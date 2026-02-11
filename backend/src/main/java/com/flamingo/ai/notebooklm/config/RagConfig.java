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
  }

  @Getter
  @Setter
  public static class Compaction {
    private int slidingWindowSize = 10;
    private int tokenThreshold = 3000;
    private int messageThreshold = 30;
    private int batchSize = 20;
  }
}
