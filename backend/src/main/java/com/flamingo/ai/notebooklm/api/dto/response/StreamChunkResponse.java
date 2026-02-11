package com.flamingo.ai.notebooklm.api.dto.response;

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

  /** Creates a citation event. */
  public static StreamChunkResponse citation(String source, Integer page, String text) {
    return StreamChunkResponse.builder()
        .eventType("citation")
        .data(new CitationData(source, page, text))
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
    private String source;
    private Integer page;
    private String text;
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
