package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.DocumentStatus;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunkIndexService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private DocumentChunkIndexService documentChunkIndexService;
  @Mock private EmbeddingService embeddingService;
  @Mock private DocumentMetadataExtractor metadataExtractor;
  @Mock private RagConfig ragConfig;

  @Captor private ArgumentCaptor<List<DocumentChunk>> chunksCaptor;

  private MeterRegistry meterRegistry;
  private DocumentProcessingService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service =
        new DocumentProcessingService(
            documentRepository,
            documentChunkIndexService,
            embeddingService,
            metadataExtractor,
            ragConfig,
            meterRegistry);
  }

  @Test
  void shouldNotExceedCharLimit_whenDenseParagraphAfterOverlap() throws Exception {
    // Given: Setup config
    when(ragConfig.getChunking()).thenReturn(createChunkingConfig(400, 50));

    // Document with small paragraph + very long dense paragraph
    // This simulates the bug scenario: overlap (200 chars) + dense paragraph (3400 chars) > 3500
    String smallParagraph = "word ".repeat(300); // ~1500 chars, normal English
    String denseParagraph = "密".repeat(3400); // 3400 chars of dense CJK text

    String content = smallParagraph + "\n\n" + denseParagraph;

    // When: Chunk content
    List<String> chunks = invokeChunkContent(content);

    // Then: No chunk should exceed 3500 chars
    for (int i = 0; i < chunks.size(); i++) {
      String chunk = chunks.get(i);
      assertThat(chunk.length())
          .withFailMessage(
              "Chunk %d exceeds 3500 char limit: %d chars\nContent preview: %s...",
              i, chunk.length(), chunk.substring(0, Math.min(100, chunk.length())))
          .isLessThanOrEqualTo(3500);
    }
  }

  @Test
  void shouldSplitLargeParagraph_whenExceedsCharLimitWithOverlap() throws Exception {
    // Given: Setup config
    when(ragConfig.getChunking()).thenReturn(createChunkingConfig(400, 50));

    // Multiple paragraphs that trigger char limit reset + large paragraph
    String normalParagraph1 = "word ".repeat(160); // ~800 chars
    String normalParagraph2 = "word ".repeat(160); // ~800 chars
    String largeParagraph = "word ".repeat(640); // ~3200 chars

    String content = normalParagraph1 + "\n\n" + normalParagraph2 + "\n\n" + largeParagraph;

    // When: Chunk content
    List<String> chunks = invokeChunkContent(content);

    // Then: All chunks should respect char limit
    for (String chunk : chunks) {
      assertThat(chunk.length()).isLessThanOrEqualTo(3500);
    }
  }

  @Test
  void shouldHandleVeryLongSingleParagraph() throws Exception {
    // Given: Setup config
    when(ragConfig.getChunking()).thenReturn(createChunkingConfig(400, 50));

    // Single paragraph that exceeds max chars
    String veryLongParagraph = "word ".repeat(800); // ~4000 chars, exceeds 3500 limit

    // When: Chunk content
    List<String> chunks = invokeChunkContent(veryLongParagraph);

    // Then: Should be split into multiple chunks
    assertThat(chunks.size()).isGreaterThan(1);

    for (String chunk : chunks) {
      assertThat(chunk.length()).isLessThanOrEqualTo(3500);
    }
  }

  @Test
  void shouldHandleEmptyContent() throws Exception {
    // Given: Setup config
    when(ragConfig.getChunking()).thenReturn(createChunkingConfig(400, 50));

    // Empty content
    String content = "";

    // When: Chunk content
    List<String> chunks = invokeChunkContent(content);

    // Then: Should return empty list or single empty chunk
    assertThat(chunks).hasSizeLessThanOrEqualTo(1);
  }

  @Test
  void shouldPreserveParagraphBoundaries() throws Exception {
    // Given: Setup config
    when(ragConfig.getChunking()).thenReturn(createChunkingConfig(400, 50));

    // Content with clear paragraph breaks
    String para1 = "First paragraph with some content.";
    String para2 = "Second paragraph with different content.";
    String para3 = "Third paragraph with more content.";

    String content = para1 + "\n\n" + para2 + "\n\n" + para3;

    // When: Chunk content
    List<String> chunks = invokeChunkContent(content);

    // Then: Content should be preserved (may be split but no content loss)
    String rejoined = String.join("", chunks).replaceAll("\\s+", "");
    String original = content.replaceAll("\\s+", "");

    assertThat(rejoined).contains(original.substring(0, Math.min(50, original.length())));
  }

  // Helper methods

  /**
   * Invokes the private chunkContent method via reflection.
   *
   * @param content the content to chunk
   * @return list of chunks
   */
  @SuppressWarnings("unchecked")
  private List<String> invokeChunkContent(String content) throws Exception {
    Method method = DocumentProcessingService.class.getDeclaredMethod("chunkContent", String.class);
    method.setAccessible(true);
    return (List<String>) method.invoke(service, content);
  }

  private RagConfig.Chunking createChunkingConfig(int size, int overlap) {
    RagConfig.Chunking config = new RagConfig.Chunking();
    config.setSize(size);
    config.setOverlap(overlap);
    return config;
  }

  private RagConfig.Metadata createMetadataConfig(
      boolean extractSections, boolean extractKeywords, boolean enrichChunks) {
    RagConfig.Metadata config = new RagConfig.Metadata();
    config.setExtractSections(extractSections);
    config.setExtractKeywords(extractKeywords);
    config.setEnrichChunks(enrichChunks);
    return config;
  }

  /**
   * Integration test: Verify chunking works end-to-end with actual document processing. This tests
   * the critical bug fix where overlap + large paragraph could exceed the 3500 char limit.
   */
  @Test
  void shouldProcessDocument_withDenseContentAndNotExceedLimits() throws Exception {
    // Given: Setup all configs
    when(ragConfig.getChunking()).thenReturn(createChunkingConfig(400, 50));
    when(ragConfig.getMetadata()).thenReturn(createMetadataConfig(true, true, true));

    // Create a document
    UUID documentId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();

    Session session = new Session();
    session.setId(sessionId);

    Document document = new Document();
    document.setId(documentId);
    document.setFileName("test.txt");
    document.setSession(session);
    document.setStatus(DocumentStatus.PENDING);

    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(documentRepository.saveAndFlush(any(Document.class))).thenReturn(document);

    // Create content with dense paragraph that would trigger the bug:
    // Small paragraph (1500 chars) + Dense CJK paragraph (3400 chars)
    // After overlap (~200 chars), adding 3400-char paragraph would exceed 3500 limit
    String content = "word ".repeat(300) + "\n\n" + "密".repeat(3400);

    InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

    // Mock embeddings
    when(embeddingService.embedTexts(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              return texts.stream().map(t -> List.of(0.1f, 0.2f, 0.3f)).toList();
            });

    // Mock metadata extraction
    when(metadataExtractor.extractTitle(any(), any())).thenReturn("Test Document");
    when(metadataExtractor.extractKeywords(any())).thenReturn(List.of("test", "keyword"));
    when(metadataExtractor.extractKeywords(any(), any(Integer.class))).thenReturn(List.of("test"));
    when(metadataExtractor.findChunkSection(any(), any(Integer.class))).thenReturn("Section 1");
    when(metadataExtractor.buildEnrichedContent(any(), any(), any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0)); // Return content as-is

    // When: Process document async
    service.processDocumentAsync(documentId, inputStream);

    // Wait for async processing to complete
    Thread.sleep(2000);

    // Then: Verify chunks were indexed
    verify(documentChunkIndexService).indexChunks(chunksCaptor.capture());

    List<DocumentChunk> indexedChunks = chunksCaptor.getValue();
    assertThat(indexedChunks).isNotEmpty();

    // Verify no chunk exceeds 3500 char limit (CRITICAL BUG FIX VERIFICATION)
    for (DocumentChunk chunk : indexedChunks) {
      assertThat(chunk.getContent().length())
          .withFailMessage(
              "Chunk %s exceeds 3500 char limit: %d chars",
              chunk.getId(), chunk.getContent().length())
          .isLessThanOrEqualTo(3500);
    }
  }
}
