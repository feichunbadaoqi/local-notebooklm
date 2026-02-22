package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.agent.dto.DocumentAnalysisResult;
import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.DocumentStatus;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunkIndexService;
import com.flamingo.ai.notebooklm.service.rag.chunking.DocumentChunkingStrategy;
import com.flamingo.ai.notebooklm.service.rag.chunking.DocumentChunkingStrategyRouter;
import com.flamingo.ai.notebooklm.service.rag.embedding.EmbeddingService;
import com.flamingo.ai.notebooklm.service.rag.image.ImageStorageService;
import com.flamingo.ai.notebooklm.service.rag.model.ChunkingResult;
import com.flamingo.ai.notebooklm.service.rag.model.DocumentContext;
import com.flamingo.ai.notebooklm.service.rag.model.RawDocumentChunk;
import com.flamingo.ai.notebooklm.service.rag.summary.ContextualChunkingService;
import com.flamingo.ai.notebooklm.service.rag.summary.DocumentSummaryService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
  @Mock private DocumentChunkingStrategyRouter strategyRouter;
  @Mock private DocumentChunkingStrategy chunkingStrategy;
  @Mock private ImageStorageService imageStorageService;
  @Mock private DocumentSummaryService documentSummaryService;
  @Mock private ContextualChunkingService contextualChunkingService;

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
            strategyRouter,
            imageStorageService,
            documentSummaryService,
            contextualChunkingService,
            meterRegistry);

    lenient()
        .when(documentSummaryService.analyzeDocument(anyString(), anyString()))
        .thenReturn(new DocumentAnalysisResult("", List.of()));
  }

  @Test
  void shouldProcessDocument_whenValidContent() throws Exception {
    // Given
    UUID documentId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();

    Session session = new Session();
    session.setId(sessionId);

    Document document = new Document();
    document.setId(documentId);
    document.setFileName("test.txt");
    document.setMimeType("text/plain");
    document.setSession(session);
    document.setStatus(DocumentStatus.PENDING);

    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(documentRepository.saveAndFlush(any(Document.class))).thenReturn(document);
    when(strategyRouter.route("text/plain")).thenReturn(chunkingStrategy);

    String content = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
    List<RawDocumentChunk> rawChunks =
        List.of(
            new RawDocumentChunk("First paragraph.", List.of("Test Document"), 0, List.of(), 0),
            new RawDocumentChunk("Second paragraph.", List.of("Test Document"), 1, List.of(), 17),
            new RawDocumentChunk("Third paragraph.", List.of("Test Document"), 2, List.of(), 35));
    ChunkingResult chunkingResult = new ChunkingResult(rawChunks, List.of(), content);
    when(chunkingStrategy.chunkDocument(any(InputStream.class), any(DocumentContext.class)))
        .thenReturn(chunkingResult);

    when(embeddingService.embedTexts(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              return texts.stream().map(t -> List.of(0.1f, 0.2f, 0.3f)).toList();
            });

    // When
    InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    service.processDocumentAsync(documentId, inputStream);

    Thread.sleep(1000);

    // Then
    verify(documentChunkIndexService).indexChunks(chunksCaptor.capture());
    List<DocumentChunk> indexedChunks = chunksCaptor.getValue();
    assertThat(indexedChunks).hasSize(3);
    assertThat(indexedChunks.get(0).getSectionBreadcrumb()).contains("Test Document");
    // Document title should be extracted from first chunk's breadcrumb
    assertThat(indexedChunks.get(0).getDocumentTitle()).isEqualTo("Test Document");
  }

  @Test
  void shouldExtractTitleFromFilename_whenNoBreadcrumb() throws Exception {
    // Given
    UUID documentId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();

    Session session = new Session();
    session.setId(sessionId);

    Document document = new Document();
    document.setId(documentId);
    document.setFileName("my_test_document.txt");
    document.setMimeType("text/plain");
    document.setSession(session);
    document.setStatus(DocumentStatus.PENDING);

    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(documentRepository.saveAndFlush(any(Document.class))).thenReturn(document);
    when(strategyRouter.route("text/plain")).thenReturn(chunkingStrategy);

    String content = "Some content without sections.";
    List<RawDocumentChunk> rawChunks =
        List.of(new RawDocumentChunk(content, List.of(), 0, List.of(), 0)); // Empty breadcrumb
    ChunkingResult chunkingResult = new ChunkingResult(rawChunks, List.of(), content);
    when(chunkingStrategy.chunkDocument(any(InputStream.class), any(DocumentContext.class)))
        .thenReturn(chunkingResult);

    when(embeddingService.embedTexts(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> texts = invocation.getArgument(0);
              return texts.stream().map(t -> List.of(0.1f, 0.2f, 0.3f)).toList();
            });

    // When
    InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    service.processDocumentAsync(documentId, inputStream);

    Thread.sleep(1000);

    // Then
    verify(documentChunkIndexService).indexChunks(chunksCaptor.capture());
    List<DocumentChunk> indexedChunks = chunksCaptor.getValue();
    assertThat(indexedChunks).hasSize(1);
    // Document title should fall back to cleaned filename
    assertThat(indexedChunks.get(0).getDocumentTitle()).isEqualTo("My test document");
  }

  @Test
  void shouldFail_whenStrategyReturnsNoContent() throws Exception {
    // Given
    UUID documentId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();

    Session session = new Session();
    session.setId(sessionId);

    Document document = new Document();
    document.setId(documentId);
    document.setFileName("empty.pdf");
    document.setMimeType("application/pdf");
    document.setSession(session);
    document.setStatus(DocumentStatus.PENDING);

    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    when(documentRepository.saveAndFlush(any(Document.class))).thenReturn(document);
    when(strategyRouter.route("application/pdf")).thenReturn(chunkingStrategy);

    ChunkingResult emptyResult = new ChunkingResult(List.of(), List.of(), "");
    when(chunkingStrategy.chunkDocument(any(InputStream.class), any(DocumentContext.class)))
        .thenReturn(emptyResult);

    // When
    InputStream inputStream = new ByteArrayInputStream(new byte[0]);
    service.processDocumentAsync(documentId, inputStream);

    Thread.sleep(500);

    // Then: document should be marked as FAILED
    verify(documentRepository, atLeastOnce()).saveAndFlush(any(Document.class));
    // The failure counter should be incremented
    assertThat(meterRegistry.counter("document.processing.failure").count()).isEqualTo(1.0);
  }
}
