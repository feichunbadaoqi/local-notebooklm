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

/** Represents an extracted memory/fact from a session. */
@Entity
@Table(name = "memories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Memory {

  public static final String TYPE_FACT = "fact";
  public static final String TYPE_PREFERENCE = "preference";
  public static final String TYPE_INSIGHT = "insight";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private Session session;

  /** The content of the memory (fact, preference, or insight). */
  @Column(columnDefinition = "TEXT", nullable = false)
  private String memoryContent;

  /** Type of memory: fact, preference, or insight. */
  @Column(nullable = false)
  private String memoryType;

  /** Importance score from 0.0 to 1.0. */
  @Builder.Default private Float importance = 0.5f;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private LocalDateTime lastAccessedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    lastAccessedAt = LocalDateTime.now();
  }

  /** Updates the last accessed timestamp. */
  public void touch() {
    lastAccessedAt = LocalDateTime.now();
  }

  /** Increases the importance score (capped at 1.0). */
  public void increaseImportance(float delta) {
    this.importance = Math.min(1.0f, this.importance + delta);
  }
}
