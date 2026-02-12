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

  /** Creates a SessionResponse from a Session entity with counts. */
  public static SessionResponse fromEntity(Session session, long documentCount, long messageCount) {
    return SessionResponse.builder()
        .id(session.getId())
        .title(session.getTitle())
        .currentMode(session.getCurrentMode())
        .documentCount((int) documentCount)
        .messageCount((int) messageCount)
        .createdAt(session.getCreatedAt())
        .updatedAt(session.getUpdatedAt())
        .lastAccessedAt(session.getLastAccessedAt())
        .build();
  }

  /** Creates a SessionResponse from a Session entity without counts (defaults to 0). */
  public static SessionResponse fromEntityWithoutCounts(Session session) {
    return SessionResponse.builder()
        .id(session.getId())
        .title(session.getTitle())
        .currentMode(session.getCurrentMode())
        .documentCount(0)
        .messageCount(0)
        .createdAt(session.getCreatedAt())
        .updatedAt(session.getUpdatedAt())
        .lastAccessedAt(session.getLastAccessedAt())
        .build();
  }
}
