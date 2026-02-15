package com.flamingo.ai.notebooklm.service.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.DocumentStatus;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunkIndexService;
import com.flamingo.ai.notebooklm.exception.DocumentNotFoundException;
import com.flamingo.ai.notebooklm.exception.DocumentProcessingException;
import com.flamingo.ai.notebooklm.service.rag.DocumentProcessingService;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceImplTest {

  @Mock private DocumentRepository documentRepository;

  @Mock private SessionService sessionService;

  @Mock private MeterRegistry meterRegistry;

  @Mock private Counter counter;

  @Mock private DocumentProcessingService documentProcessingService;

  @Mock private DocumentChunkIndexService documentChunkIndexService;

  private DocumentServiceImpl documentService;

  private Session testSession;
  private UUID sessionId;

  @BeforeEach
  void setUp() {
    documentService =
        new DocumentServiceImpl(
            documentRepository,
            sessionService,
            meterRegistry,
            documentProcessingService,
            documentChunkIndexService);

    sessionId = UUID.randomUUID();
    testSession =
        Session.builder()
            .id(sessionId)
            .title("Test Session")
            .currentMode(InteractionMode.EXPLORING)
            .build();

    when(meterRegistry.counter(any(String.class), any(String.class), any(String.class)))
        .thenReturn(counter);
    when(meterRegistry.counter(any(String.class))).thenReturn(counter);
  }

  @Test
  void shouldUploadDocument_whenValidPdf() {
    // Given
    MultipartFile file =
        new MockMultipartFile("file", "test.pdf", "application/pdf", "PDF content".getBytes());

    when(sessionService.getSession(sessionId)).thenReturn(testSession);

    Document savedDocument =
        Document.builder()
            .id(UUID.randomUUID())
            .session(testSession)
            .fileName("test.pdf")
            .mimeType("application/pdf")
            .fileSize((long) "PDF content".getBytes().length)
            .status(DocumentStatus.PENDING)
            .build();

    when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);

    // When
    Document result = documentService.uploadDocument(sessionId, file);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getFileName()).isEqualTo("test.pdf");
    assertThat(result.getMimeType()).isEqualTo("application/pdf");
    verify(documentRepository).save(any(Document.class));
  }

  @Test
  void shouldUploadDocument_whenValidDocx() {
    // Given
    MultipartFile file =
        new MockMultipartFile(
            "file",
            "test.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "DOCX content".getBytes());

    when(sessionService.getSession(sessionId)).thenReturn(testSession);

    Document savedDocument =
        Document.builder()
            .id(UUID.randomUUID())
            .session(testSession)
            .fileName("test.docx")
            .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .build();

    when(documentRepository.save(any(Document.class))).thenReturn(savedDocument);

    // When
    Document result = documentService.uploadDocument(sessionId, file);

    // Then
    assertThat(result.getFileName()).isEqualTo("test.docx");
  }

  @Test
  void shouldThrowException_whenFileIsEmpty() {
    // Given
    MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

    when(sessionService.getSession(sessionId)).thenReturn(testSession);

    // When/Then
    assertThatThrownBy(() -> documentService.uploadDocument(sessionId, file))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void shouldThrowException_whenUnsupportedFileType() {
    // Given
    MultipartFile file =
        new MockMultipartFile("file", "test.exe", "application/x-msdownload", "content".getBytes());

    when(sessionService.getSession(sessionId)).thenReturn(testSession);

    // When/Then
    assertThatThrownBy(() -> documentService.uploadDocument(sessionId, file))
        .isInstanceOf(DocumentProcessingException.class);
  }

  @Test
  void shouldGetDocument_whenDocumentExists() {
    // Given
    UUID documentId = UUID.randomUUID();
    Document document =
        Document.builder()
            .id(documentId)
            .session(testSession)
            .fileName("test.pdf")
            .mimeType("application/pdf")
            .status(DocumentStatus.READY)
            .build();

    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    // When
    Document result = documentService.getDocument(documentId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(documentId);
  }

  @Test
  void shouldThrowException_whenDocumentNotFound() {
    // Given
    UUID documentId = UUID.randomUUID();
    when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

    // When/Then
    assertThatThrownBy(() -> documentService.getDocument(documentId))
        .isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void shouldGetDocumentsBySession() {
    // Given
    Document doc1 =
        Document.builder()
            .id(UUID.randomUUID())
            .session(testSession)
            .fileName("doc1.pdf")
            .mimeType("application/pdf")
            .build();

    Document doc2 =
        Document.builder()
            .id(UUID.randomUUID())
            .session(testSession)
            .fileName("doc2.pdf")
            .mimeType("application/pdf")
            .build();

    when(sessionService.getSession(sessionId)).thenReturn(testSession);
    when(documentRepository.findBySessionIdOrderByUploadedAtDesc(sessionId))
        .thenReturn(List.of(doc1, doc2));

    // When
    List<Document> result = documentService.getDocumentsBySession(sessionId);

    // Then
    assertThat(result).hasSize(2);
  }

  @Test
  void shouldDeleteDocument() {
    // Given
    UUID documentId = UUID.randomUUID();
    Document document =
        Document.builder()
            .id(documentId)
            .session(testSession)
            .fileName("test.pdf")
            .mimeType("application/pdf")
            .build();

    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    // When
    documentService.deleteDocument(documentId);

    // Then
    verify(documentRepository).delete(document);
  }

  @Test
  void shouldGetDocumentStatus() {
    // Given
    UUID documentId = UUID.randomUUID();
    Document document =
        Document.builder()
            .id(documentId)
            .session(testSession)
            .fileName("test.pdf")
            .mimeType("application/pdf")
            .status(DocumentStatus.PROCESSING)
            .build();

    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

    // When
    Document result = documentService.getDocumentStatus(documentId);

    // Then
    assertThat(result.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
  }
}
