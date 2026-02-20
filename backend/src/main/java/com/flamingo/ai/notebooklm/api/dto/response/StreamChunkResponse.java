package com.flamingo.ai.notebooklm.api.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for SSE streaming chunks. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunkResponse {

  /** Event type: token, citation, done, error. */
  private String eventType;

  /** Event data (JSON object). */
  private Object data;

  /** Creates a token event. */
  public static StreamChunkResponse token(String content) {
    return StreamChunkResponse.builder().eventType("token").data(new TokenData(content)).build();
  }

  /** Creates a citation event without image IDs or breadcrumb. */
  public static StreamChunkResponse citation(
      String documentId, String source, Integer page, String text) {
    return citation(documentId, source, page, text, List.of(), List.of());
  }

  /** Creates a citation event with associated image IDs but no breadcrumb. */
  public static StreamChunkResponse citation(
      String documentId, String source, Integer page, String text, List<String> imageIds) {
    return citation(documentId, source, page, text, imageIds, List.of());
  }

  /** Creates a citation event with image IDs and section breadcrumb. */
  public static StreamChunkResponse citation(
      String documentId,
      String source,
      Integer page,
      String text,
      List<String> imageIds,
      List<String> sectionBreadcrumb) {
    return StreamChunkResponse.builder()
        .eventType("citation")
        .data(
            new CitationData(
                documentId,
                source,
                page,
                text,
                imageIds != null ? imageIds : List.of(),
                sectionBreadcrumb != null ? sectionBreadcrumb : List.of()))
        .build();
  }

  /** Creates a done event. */
  public static StreamChunkResponse done(String messageId, int promptTokens, int completionTokens) {
    return StreamChunkResponse.builder()
        .eventType("done")
        .data(new DoneData(messageId, promptTokens, completionTokens))
        .build();
  }

  /** Creates an error event. */
  public static StreamChunkResponse error(String errorId, String message) {
    return StreamChunkResponse.builder()
        .eventType("error")
        .data(new ErrorData(errorId, message))
        .build();
  }

  /** Token event data. */
  @Data
  @AllArgsConstructor
  public static class TokenData {
    private String content;
  }

  /** Citation event data. */
  @Data
  @AllArgsConstructor
  public static class CitationData {
    private String documentId;
    private String source;
    private Integer page;
    private String text;

    /** UUIDs of {@code DocumentImage} entities associated with the cited chunk. */
    private List<String> imageIds;

    /**
     * Hierarchical breadcrumb path showing the section context, e.g. ["Chapter 1", "Security",
     * "Best Practices"].
     */
    private List<String> sectionBreadcrumb;
  }

  /** Done event data. */
  @Data
  @AllArgsConstructor
  public static class DoneData {
    private String messageId;
    private int promptTokens;
    private int completionTokens;
  }

  /** Error event data. */
  @Data
  @AllArgsConstructor
  public static class ErrorData {
    private String errorId;
    private String message;
  }
}
