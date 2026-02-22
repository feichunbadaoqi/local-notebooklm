package com.flamingo.ai.notebooklm.api.dto.response;

import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.domain.enums.DocumentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for document data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

  private UUID id;
  private UUID sessionId;
  private String fileName;
  private String mimeType;
  private Long fileSize;
  private DocumentStatus status;
  private Integer chunkCount;
  private String summary;
  private List<String> topics;
  private String processingError;
  private LocalDateTime uploadedAt;
  private LocalDateTime processedAt;

  /** Creates a DocumentResponse from a Document entity. */
  public static DocumentResponse fromEntity(Document document) {
    return DocumentResponse.builder()
        .id(document.getId())
        .sessionId(document.getSession().getId())
        .fileName(document.getFileName())
        .mimeType(document.getMimeType())
        .fileSize(document.getFileSize())
        .status(document.getStatus())
        .chunkCount(document.getChunkCount())
        .summary(document.getSummary())
        .topics(document.getTopics())
        .processingError(document.getProcessingError())
        .uploadedAt(document.getUploadedAt())
        .processedAt(document.getProcessedAt())
        .build();
  }
}
