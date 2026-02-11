package com.flamingo.ai.notebooklm.api.dto.response;

import com.flamingo.ai.notebooklm.domain.entity.Memory;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for memory data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryResponse {

  private UUID id;
  private UUID sessionId;
  private String memoryContent;
  private String memoryType;
  private Float importance;
  private LocalDateTime createdAt;
  private LocalDateTime lastAccessedAt;

  /** Creates a MemoryResponse from a Memory entity. */
  public static MemoryResponse fromEntity(Memory memory) {
    return MemoryResponse.builder()
        .id(memory.getId())
        .sessionId(memory.getSession().getId())
        .memoryContent(memory.getMemoryContent())
        .memoryType(memory.getMemoryType())
        .importance(memory.getImportance())
        .createdAt(memory.getCreatedAt())
        .lastAccessedAt(memory.getLastAccessedAt())
        .build();
  }
}
