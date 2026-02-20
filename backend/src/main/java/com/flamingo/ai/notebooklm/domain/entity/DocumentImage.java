package com.flamingo.ai.notebooklm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an image extracted from a processed document and stored on disk.
 *
 * <p>Images are stored at: {@code
 * {app.image-storage.base-path}/{sessionId}/{documentId}/{imageIndex}.{ext}}
 *
 * <p>The REST endpoint {@code GET /api/sessions/{sessionId}/images/{imageId}} serves the image
 * bytes with the correct {@code Content-Type} derived from {@link #mimeType}.
 */
@Entity
@Table(name = "document_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentImage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID documentId;

  @Column(nullable = false)
  private UUID sessionId;

  /** Sequential image index within the source document (0-based). */
  @Column(nullable = false)
  private int imageIndex;

  /** MIME type of the stored image (e.g. {@code image/png}, {@code image/jpeg}). */
  @Column(nullable = false)
  private String mimeType;

  /** Alt-text or caption extracted from the surrounding document structure. */
  private String altText;

  /** Absolute file-system path to the stored image file. */
  @Column(columnDefinition = "TEXT", nullable = false)
  private String filePath;

  private int width;
  private int height;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
