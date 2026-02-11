package com.flamingo.ai.notebooklm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Represents a summary of compacted chat messages. */
@Entity
@Table(name = "chat_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSummary {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private Session session;

  /** The summarized content of the compacted messages. */
  @Column(columnDefinition = "TEXT", nullable = false)
  private String summaryContent;

  /** Number of messages that were summarized. */
  private Integer messageCount;

  /** Token count of the summary. */
  private Integer tokenCount;

  /** Original token count of the messages before compaction. */
  private Integer originalTokenCount;

  /** Timestamp of the earliest message in this summary. */
  @Column(nullable = false)
  private LocalDateTime fromTimestamp;

  /** Timestamp of the latest message in this summary. */
  @Column(nullable = false)
  private LocalDateTime toTimestamp;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }

  /** Calculates the compression ratio achieved by this summary. */
  public double getCompressionRatio() {
    if (originalTokenCount == null || originalTokenCount == 0) {
      return 0.0;
    }
    return 1.0 - ((double) tokenCount / originalTokenCount);
  }
}
