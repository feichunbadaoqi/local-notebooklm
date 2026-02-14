package com.flamingo.ai.notebooklm.api.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for system-wide statistics. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStats {
  private long totalSessions;
  private long totalMessages;
  private long totalDocuments;
  private LocalDateTime timestamp;
}
