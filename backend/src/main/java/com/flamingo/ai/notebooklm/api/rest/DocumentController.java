package com.flamingo.ai.notebooklm.api.rest;

import com.flamingo.ai.notebooklm.api.dto.response.DocumentResponse;
import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.service.document.DocumentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** REST controller for document management. */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;

  /** Uploads a document to a session. */
  @PostMapping(
      value = "/sessions/{sessionId}/documents",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentResponse> uploadDocument(
      @PathVariable UUID sessionId, @RequestParam("file") MultipartFile file) {
    Document document = documentService.uploadDocument(sessionId, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.fromEntity(document));
  }

  /** Gets all documents for a session. */
  @GetMapping("/sessions/{sessionId}/documents")
  public ResponseEntity<List<DocumentResponse>> getDocumentsBySession(
      @PathVariable UUID sessionId) {
    List<Document> documents = documentService.getDocumentsBySession(sessionId);
    List<DocumentResponse> responses =
        documents.stream().map(DocumentResponse::fromEntity).toList();
    return ResponseEntity.ok(responses);
  }

  /** Gets a document by ID. */
  @GetMapping("/documents/{documentId}")
  public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID documentId) {
    Document document = documentService.getDocument(documentId);
    return ResponseEntity.ok(DocumentResponse.fromEntity(document));
  }

  /** Gets the processing status of a document. */
  @GetMapping("/documents/{documentId}/status")
  public ResponseEntity<DocumentResponse> getDocumentStatus(@PathVariable UUID documentId) {
    Document document = documentService.getDocumentStatus(documentId);
    return ResponseEntity.ok(DocumentResponse.fromEntity(document));
  }

  /** Deletes a document. */
  @DeleteMapping("/documents/{documentId}")
  public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentId) {
    documentService.deleteDocument(documentId);
    return ResponseEntity.noContent().build();
  }
}
