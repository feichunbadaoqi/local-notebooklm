package com.flamingo.ai.notebooklm.domain.entity;

import com.flamingo.ai.notebooklm.domain.enums.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Represents an uploaded document in a session. */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private Session session;

  @Column(nullable = false)
  private String fileName;

  @Column(nullable = false)
  private String mimeType;

  private Long fileSize;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private DocumentStatus status = DocumentStatus.PENDING;

  /** Number of chunks created from this document. */
  private Integer chunkCount;

  /** LLM-generated summary of the document content. */
  @Column(columnDefinition = "TEXT")
  private String summary;

  /** Error message if processing failed. */
  @Column(columnDefinition = "TEXT")
  private String processingError;

  @Column(nullable = false, updatable = false)
  private LocalDateTime uploadedAt;

  private LocalDateTime processedAt;

  @PrePersist
  protected void onCreate() {
    uploadedAt = LocalDateTime.now();
  }

  /** Marks the document as processing. */
  public void startProcessing() {
    this.status = DocumentStatus.PROCESSING;
  }

  /** Marks the document as successfully processed. */
  public void markReady(int chunkCount) {
    this.status = DocumentStatus.READY;
    this.chunkCount = chunkCount;
    this.processedAt = LocalDateTime.now();
  }

  /** Marks the document as failed with an error message. */
  public void markFailed(String errorMessage) {
    this.status = DocumentStatus.FAILED;
    this.processingError = errorMessage;
    this.processedAt = LocalDateTime.now();
  }
}
