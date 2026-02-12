package com.flamingo.ai.notebooklm.service.document;

import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.elasticsearch.ElasticsearchIndexService;
import com.flamingo.ai.notebooklm.exception.DocumentNotFoundException;
import com.flamingo.ai.notebooklm.exception.DocumentProcessingException;
import com.flamingo.ai.notebooklm.service.rag.DocumentProcessingService;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Implementation of the DocumentService. */
@Service
@Slf4j
public class DocumentServiceImpl implements DocumentService {

  private static final Set<String> SUPPORTED_MIME_TYPES =
      Set.of(
          "application/pdf",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/epub+zip",
          "text/plain");

  private final DocumentRepository documentRepository;
  private final SessionService sessionService;
  private final MeterRegistry meterRegistry;
  private final DocumentProcessingService documentProcessingService;
  private final ElasticsearchIndexService elasticsearchIndexService;

  public DocumentServiceImpl(
      DocumentRepository documentRepository,
      SessionService sessionService,
      MeterRegistry meterRegistry,
      @Lazy DocumentProcessingService documentProcessingService,
      @Lazy ElasticsearchIndexService elasticsearchIndexService) {
    this.documentRepository = documentRepository;
    this.sessionService = sessionService;
    this.meterRegistry = meterRegistry;
    this.documentProcessingService = documentProcessingService;
    this.elasticsearchIndexService = elasticsearchIndexService;
  }

  @Override
  @Transactional
  @Timed(value = "document.upload", description = "Time to upload a document")
  public Document uploadDocument(UUID sessionId, MultipartFile file) {
    log.info("Uploading document {} for session {}", file.getOriginalFilename(), sessionId);

    Session session = sessionService.getSession(sessionId);

    // Validate file
    validateFile(file);

    // Create document record
    Document document =
        Document.builder()
            .session(session)
            .fileName(file.getOriginalFilename())
            .mimeType(file.getContentType())
            .fileSize(file.getSize())
            .build();

    Document saved = documentRepository.save(document);
    meterRegistry
        .counter("document.uploaded", "type", getFileType(file.getContentType()))
        .increment();

    // Read file bytes now (before transaction ends) so we can process after commit
    final byte[] fileBytes;
    try {
      fileBytes = file.getBytes();
    } catch (IOException e) {
      log.error("Failed to read file bytes: {}", e.getMessage());
      saved.markFailed("Failed to read file content");
      return documentRepository.save(saved);
    }

    // Trigger async processing AFTER transaction commits to avoid race condition
    final UUID documentId = saved.getId();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              log.debug(
                  "Transaction committed, starting async processing for document: {}", documentId);
              documentProcessingService.processDocumentAsync(
                  documentId, new ByteArrayInputStream(fileBytes));
            }
          });
    } else {
      // In tests or non-transactional context, process directly
      log.debug("No active transaction, processing document directly: {}", documentId);
      documentProcessingService.processDocumentAsync(
          documentId, new ByteArrayInputStream(fileBytes));
    }

    log.info("Document {} uploaded with ID: {}", file.getOriginalFilename(), saved.getId());
    return saved;
  }

  @Override
  @Transactional(readOnly = true)
  @Timed(value = "document.get", description = "Time to get a document")
  public Document getDocument(UUID documentId) {
    return documentRepository
        .findById(documentId)
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
  }

  @Override
  @Transactional(readOnly = true)
  @Timed(value = "document.getBySession", description = "Time to get documents by session")
  public List<Document> getDocumentsBySession(UUID sessionId) {
    // Verify session exists
    sessionService.getSession(sessionId);
    return documentRepository.findBySessionIdOrderByUploadedAtDesc(sessionId);
  }

  @Override
  @Transactional
  @Timed(value = "document.delete", description = "Time to delete a document")
  public void deleteDocument(UUID documentId) {
    Document document = getDocument(documentId);

    // Delete chunks from Elasticsearch
    elasticsearchIndexService.deleteByDocumentId(documentId);

    documentRepository.delete(document);
    meterRegistry.counter("document.deleted").increment();

    log.info("Deleted document: {}", documentId);
  }

  @Override
  @Transactional(readOnly = true)
  public Document getDocumentStatus(UUID documentId) {
    return getDocument(documentId);
  }

  private void validateFile(MultipartFile file) {
    if (file.isEmpty()) {
      throw new DocumentProcessingException(null, "File is empty", "Please upload a valid file");
    }

    String contentType = file.getContentType();
    if (contentType == null || !SUPPORTED_MIME_TYPES.contains(contentType)) {
      throw new DocumentProcessingException(
          null, "Unsupported file type: " + contentType, "Supported formats: PDF, DOCX, EPUB, TXT");
    }

    // Max 50MB
    if (file.getSize() > 50 * 1024 * 1024) {
      throw new DocumentProcessingException(
          null, "File too large: " + file.getSize(), "Maximum file size is 50MB");
    }
  }

  private String getFileType(String mimeType) {
    if (mimeType == null) {
      return "unknown";
    }
    return switch (mimeType) {
      case "application/pdf" -> "pdf";
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
      case "application/epub+zip" -> "epub";
      case "text/plain" -> "txt";
      default -> "other";
    };
  }
}
