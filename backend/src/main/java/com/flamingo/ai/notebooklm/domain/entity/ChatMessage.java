package com.flamingo.ai.notebooklm.domain.entity;

import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
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

/** Represents a single message in a chat session. */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private Session session;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MessageRole role;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  /** The interaction mode used when this message was generated (for assistant messages). */
  @Enumerated(EnumType.STRING)
  private InteractionMode modeUsed;

  /** Estimated token count for this message. */
  private Integer tokenCount;

  /** Whether this message has been compacted into a summary. */
  @Builder.Default private Boolean isCompacted = false;

  /** Reference to the summary this message was compacted into. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "summary_id")
  private ChatSummary compactedInto;

  /** JSON-serialized retrieved context (for assistant messages). */
  @Column(columnDefinition = "TEXT")
  private String retrievedContextJson;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }

  /** Marks this message as compacted into a summary. */
  public void markCompacted(ChatSummary summary) {
    this.isCompacted = true;
    this.compactedInto = summary;
  }
}
