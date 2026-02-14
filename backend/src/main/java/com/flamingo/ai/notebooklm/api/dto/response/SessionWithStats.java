package com.flamingo.ai.notebooklm.api.dto.response;

import com.flamingo.ai.notebooklm.domain.entity.Session;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO containing session data with aggregated statistics. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionWithStats {
  private Session session;
  private long documentCount;
  private long messageCount;

  /** Converts to SessionResponse for API output. */
  public SessionResponse toResponse() {
    return SessionResponse.fromEntity(session, documentCount, messageCount);
  }
}
