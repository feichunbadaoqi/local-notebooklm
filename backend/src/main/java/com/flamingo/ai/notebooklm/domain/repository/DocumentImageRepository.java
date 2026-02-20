package com.flamingo.ai.notebooklm.domain.repository;

import com.flamingo.ai.notebooklm.domain.entity.DocumentImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link DocumentImage} entities. */
public interface DocumentImageRepository extends JpaRepository<DocumentImage, UUID> {

  /**
   * Returns all images extracted from a specific document.
   *
   * @param documentId the document ID
   * @return images ordered by {@code imageIndex}
   */
  List<DocumentImage> findByDocumentIdOrderByImageIndex(UUID documentId);

  /**
   * Deletes all images associated with a document.
   *
   * @param documentId the document ID
   */
  void deleteByDocumentId(UUID documentId);

  /**
   * Deletes all images associated with a session.
   *
   * @param sessionId the session ID
   */
  void deleteBySessionId(UUID sessionId);
}
