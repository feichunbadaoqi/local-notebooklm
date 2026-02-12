package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.elasticsearch.ElasticsearchIndexService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test verifying session isolation in hybrid search.
 *
 * <p>This test uses Testcontainers to spin up a real Elasticsearch instance and verifies that
 * documents uploaded to Session A are only searchable from Session A, and documents uploaded to
 * Session B are only searchable from Session B. Vector search, keyword search, and hybrid search
 * all respect session boundaries.
 */
@ExtendWith(MockitoExtension.class)
@Testcontainers
@DisplayName("Session Isolation Integration Test")
class SessionIsolationIntegrationTest {

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH_CONTAINER =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.12.2")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("xpack.security.http.ssl.enabled", "false")
          .withStartupTimeout(Duration.ofMinutes(2));

  @Mock private EmbeddingModel embeddingModel;

  private ElasticsearchClient elasticsearchClient;
  private ElasticsearchIndexService indexService;
  private EmbeddingService embeddingService;
  private DiversityReranker diversityReranker;
  private HybridSearchService hybridSearchService;
  private MeterRegistry meterRegistry;

  private UUID sessionA;
  private UUID sessionB;
  private UUID documentA1;
  private UUID documentA2;
  private UUID documentB1;

  @BeforeEach
  void setUp() throws Exception {
    RestClient restClient =
        RestClient.builder(
                new HttpHost(
                    ELASTICSEARCH_CONTAINER.getHost(),
                    ELASTICSEARCH_CONTAINER.getMappedPort(9200),
                    "http"))
            .build();
    RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    elasticsearchClient = new ElasticsearchClient(transport);

    meterRegistry = new SimpleMeterRegistry();
    indexService =
        new ElasticsearchIndexService(
            elasticsearchClient, meterRegistry, "notebooklm-chunks", 3072);
    embeddingService = new EmbeddingService(embeddingModel, meterRegistry);

    RagConfig ragConfig = new RagConfig();
    ragConfig.setRetrieval(new RagConfig.Retrieval());
    ragConfig.setDiversity(new RagConfig.Diversity());

    diversityReranker = new DiversityReranker(ragConfig, meterRegistry);
    hybridSearchService =
        new HybridSearchService(
            indexService, embeddingService, diversityReranker, ragConfig, meterRegistry);

    indexService.initIndex();
    Thread.sleep(1000);

    sessionA = UUID.randomUUID();
    sessionB = UUID.randomUUID();
    documentA1 = UUID.randomUUID();
    documentA2 = UUID.randomUUID();
    documentB1 = UUID.randomUUID();

    when(embeddingModel.embed(anyString()))
        .thenAnswer(
            invocation -> {
              String text = invocation.getArgument(0);
              dev.langchain4j.data.embedding.Embedding embedding;
              if (text.contains("artificial intelligence")) {
                embedding =
                    dev.langchain4j.data.embedding.Embedding.from(
                        createMockEmbedding(0.9f, 0.1f, 0.2f));
              } else if (text.contains("machine learning")) {
                embedding =
                    dev.langchain4j.data.embedding.Embedding.from(
                        createMockEmbedding(0.8f, 0.3f, 0.1f));
              } else if (text.contains("quantum computing")) {
                embedding =
                    dev.langchain4j.data.embedding.Embedding.from(
                        createMockEmbedding(0.1f, 0.9f, 0.8f));
              } else {
                embedding =
                    dev.langchain4j.data.embedding.Embedding.from(
                        createMockEmbedding(0.5f, 0.5f, 0.5f));
              }
              return dev.langchain4j.model.output.Response.from(embedding);
            });
  }

  @AfterEach
  void tearDown() {
    if (indexService != null) {
      indexService.deleteBySessionId(sessionA);
      indexService.deleteBySessionId(sessionB);
    }
  }

  @Test
  @DisplayName("Should only retrieve documents from the same session")
  void shouldOnlyRetrieveDocumentsFromSameSession() throws Exception {
    List<DocumentChunk> sessionAChunks = new ArrayList<>();
    sessionAChunks.add(
        createChunk(
            documentA1 + "_0",
            documentA1,
            sessionA,
            "doc-a1.pdf",
            0,
            "This document discusses artificial intelligence and its applications in modern"
                + " technology. "
                + "AI has revolutionized how we process information and make decisions."));
    sessionAChunks.add(
        createChunk(
            documentA2 + "_0",
            documentA2,
            sessionA,
            "doc-a2.pdf",
            0,
            "Machine learning is a subset of artificial intelligence that enables computers to"
                + " learn from data. "
                + "Deep learning networks have shown remarkable results in image recognition."));

    List<DocumentChunk> sessionBChunks = new ArrayList<>();
    sessionBChunks.add(
        createChunk(
            documentB1 + "_0",
            documentB1,
            sessionB,
            "doc-b1.pdf",
            0,
            "Quantum computing uses quantum mechanics principles to perform computations. "
                + "Qubits can exist in superposition states, enabling parallel processing."));

    indexService.indexChunks(sessionAChunks);
    indexService.indexChunks(sessionBChunks);
    Thread.sleep(2000);

    List<DocumentChunk> resultsA =
        hybridSearchService.search(sessionA, "artificial intelligence", InteractionMode.EXPLORING);

    assertThat(resultsA).isNotEmpty();
    assertThat(resultsA).allSatisfy(chunk -> assertThat(chunk.getSessionId()).isEqualTo(sessionA));
    assertThat(resultsA)
        .allSatisfy(
            chunk ->
                assertThat(chunk.getDocumentId())
                    .describedAs("Session A should only see its own documents")
                    .isIn(documentA1, documentA2));

    List<DocumentChunk> resultsB =
        hybridSearchService.search(sessionB, "artificial intelligence", InteractionMode.EXPLORING);

    assertThat(resultsB)
        .describedAs(
            "Session B should not see Session A's documents even though they match the query")
        .isEmpty();
  }

  @Test
  @DisplayName("Vector search should respect session boundaries")
  void vectorSearchShouldRespectSessionBoundaries() throws Exception {
    List<DocumentChunk> sessionAChunks = new ArrayList<>();
    sessionAChunks.add(
        createChunk(
            documentA1 + "_0",
            documentA1,
            sessionA,
            "ai-doc.pdf",
            0,
            "Artificial intelligence and machine learning are transforming industries."));

    List<DocumentChunk> sessionBChunks = new ArrayList<>();
    sessionBChunks.add(
        createChunk(
            documentB1 + "_0",
            documentB1,
            sessionB,
            "quantum-doc.pdf",
            0,
            "Quantum computing leverages quantum mechanics for computation."));

    indexService.indexChunks(sessionAChunks);
    indexService.indexChunks(sessionBChunks);
    Thread.sleep(2000);

    List<Float> aiEmbedding = embeddingService.embedText("artificial intelligence");

    List<DocumentChunk> vectorResultsA = indexService.vectorSearch(sessionA, aiEmbedding, 10);
    List<DocumentChunk> vectorResultsB = indexService.vectorSearch(sessionB, aiEmbedding, 10);

    assertThat(vectorResultsA)
        .isNotEmpty()
        .allSatisfy(chunk -> assertThat(chunk.getSessionId()).isEqualTo(sessionA));

    assertThat(vectorResultsB)
        .describedAs("Session B should not see Session A's documents via vector search")
        .isEmpty();
  }

  @Test
  @DisplayName("Keyword search should respect session boundaries")
  void keywordSearchShouldRespectSessionBoundaries() throws Exception {
    List<DocumentChunk> sessionAChunks = new ArrayList<>();
    sessionAChunks.add(
        createChunk(
            documentA1 + "_0",
            documentA1,
            sessionA,
            "python-guide.pdf",
            0,
            "Python programming language is widely used for data science and machine learning."));

    List<DocumentChunk> sessionBChunks = new ArrayList<>();
    sessionBChunks.add(
        createChunk(
            documentB1 + "_0",
            documentB1,
            sessionB,
            "java-guide.pdf",
            0,
            "Java programming language is popular for enterprise applications."));

    indexService.indexChunks(sessionAChunks);
    indexService.indexChunks(sessionBChunks);
    Thread.sleep(2000);

    List<DocumentChunk> keywordResultsA = indexService.keywordSearch(sessionA, "Python", 10);
    List<DocumentChunk> keywordResultsB = indexService.keywordSearch(sessionB, "Python", 10);

    assertThat(keywordResultsA)
        .isNotEmpty()
        .allSatisfy(chunk -> assertThat(chunk.getContent()).containsIgnoringCase("Python"));

    assertThat(keywordResultsB)
        .describedAs("Session B should not see Session A's Python document via keyword search")
        .isEmpty();
  }

  @Test
  @DisplayName("Multiple documents in same session should all be searchable")
  void multipleDocumentsInSameSessionShouldAllBeSearchable() throws Exception {
    List<DocumentChunk> sessionAChunks = new ArrayList<>();
    sessionAChunks.add(
        createChunk(
            documentA1 + "_0",
            documentA1,
            sessionA,
            "doc1.pdf",
            0,
            "First document about artificial intelligence."));
    sessionAChunks.add(
        createChunk(
            documentA2 + "_0",
            documentA2,
            sessionA,
            "doc2.pdf",
            0,
            "Second document about machine learning."));
    sessionAChunks.add(
        createChunk(
            UUID.randomUUID() + "_0",
            UUID.randomUUID(),
            sessionA,
            "doc3.pdf",
            0,
            "Third document about neural networks."));

    indexService.indexChunks(sessionAChunks);
    Thread.sleep(2000);

    List<DocumentChunk> results =
        hybridSearchService.search(sessionA, "artificial intelligence", InteractionMode.EXPLORING);

    assertThat(results).isNotEmpty();
    assertThat(results).allSatisfy(chunk -> assertThat(chunk.getSessionId()).isEqualTo(sessionA));
    long uniqueDocCount = results.stream().map(DocumentChunk::getDocumentId).distinct().count();
    assertThat(uniqueDocCount).isPositive();
  }

  @Test
  @DisplayName("Empty session should return no results")
  void emptySessionShouldReturnNoResults() {
    UUID emptySession = UUID.randomUUID();

    List<DocumentChunk> results =
        hybridSearchService.search(emptySession, "any query", InteractionMode.EXPLORING);

    assertThat(results).isEmpty();
  }

  private DocumentChunk createChunk(
      String id, UUID documentId, UUID sessionId, String fileName, int chunkIndex, String content) {
    List<Float> embedding = embeddingService.embedText(content);
    return DocumentChunk.builder()
        .id(id)
        .documentId(documentId)
        .sessionId(sessionId)
        .fileName(fileName)
        .chunkIndex(chunkIndex)
        .content(content)
        .embedding(embedding)
        .tokenCount(content.length() / 4)
        .build();
  }

  private float[] createMockEmbedding(float v1, float v2, float v3) {
    float[] embedding = new float[3072];
    embedding[0] = v1;
    embedding[1] = v2;
    embedding[2] = v3;
    for (int i = 3; i < 3072; i++) {
      embedding[i] = (float) (0.001 * Math.sin(i));
    }
    return embedding;
  }
}
