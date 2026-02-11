package com.flamingo.ai.notebooklm.domain.repository;

import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.domain.enums.DocumentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for Document entities. */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

  /** Finds all documents for a session. */
  List<Document> findBySessionIdOrderByUploadedAtDesc(UUID sessionId);

  /** Finds documents by status. */
  List<Document> findByStatus(DocumentStatus status);

  /** Finds documents by session and status. */
  List<Document> findBySessionIdAndStatus(UUID sessionId, DocumentStatus status);

  /** Counts documents by session. */
  long countBySessionId(UUID sessionId);

  /** Counts ready documents by session. */
  long countBySessionIdAndStatus(UUID sessionId, DocumentStatus status);

  /** Finds all pending documents for processing. */
  @Query("SELECT d FROM Document d WHERE d.status = 'PENDING' ORDER BY d.uploadedAt ASC")
  List<Document> findPendingDocuments();

  /** Calculates total chunk count for a session. */
  @Query(
      "SELECT COALESCE(SUM(d.chunkCount), 0) FROM Document d "
          + "WHERE d.session.id = :sessionId AND d.status = 'READY'")
  int countTotalChunksBySessionId(@Param("sessionId") UUID sessionId);
}
