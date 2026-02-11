package com.flamingo.ai.notebooklm.service.document;

import com.flamingo.ai.notebooklm.domain.entity.Document;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Service interface for document management. */
public interface DocumentService {

  /**
   * Uploads and processes a document for a session.
   *
   * @param sessionId the session ID
   * @param file the uploaded file
   * @return the created document
   */
  Document uploadDocument(UUID sessionId, MultipartFile file);

  /**
   * Gets a document by ID.
   *
   * @param documentId the document ID
   * @return the document
   * @throws com.flamingo.ai.notebooklm.exception.DocumentNotFoundException if not found
   */
  Document getDocument(UUID documentId);

  /**
   * Gets all documents for a session.
   *
   * @param sessionId the session ID
   * @return list of documents
   */
  List<Document> getDocumentsBySession(UUID sessionId);

  /**
   * Deletes a document and its associated chunks.
   *
   * @param documentId the document ID
   */
  void deleteDocument(UUID documentId);

  /**
   * Gets the processing status of a document.
   *
   * @param documentId the document ID
   * @return the document with current status
   */
  Document getDocumentStatus(UUID documentId);
}
