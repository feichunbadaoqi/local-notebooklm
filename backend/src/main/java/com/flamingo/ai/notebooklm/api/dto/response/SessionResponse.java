package com.flamingo.ai.notebooklm.api.dto.response;

import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for session data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

  private UUID id;
  private String title;
  private InteractionMode currentMode;
  private int documentCount;
  private int messageCount;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime lastAccessedAt;

  /** Creates a SessionResponse from a Session entity. */
  public static SessionResponse fromEntity(Session session) {
    return SessionResponse.builder()
        .id(session.getId())
        .title(session.getTitle())
        .currentMode(session.getCurrentMode())
        .documentCount(session.getDocuments() != null ? session.getDocuments().size() : 0)
        .messageCount(session.getMessages() != null ? session.getMessages().size() : 0)
        .createdAt(session.getCreatedAt())
        .updatedAt(session.getUpdatedAt())
        .lastAccessedAt(session.getLastAccessedAt())
        .build();
  }
}
