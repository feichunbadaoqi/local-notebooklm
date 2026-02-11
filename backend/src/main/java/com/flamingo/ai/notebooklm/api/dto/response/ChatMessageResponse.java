package com.flamingo.ai.notebooklm.api.dto.response;

import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for chat message data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {

  private UUID id;
  private MessageRole role;
  private String content;
  private InteractionMode modeUsed;
  private Integer tokenCount;
  private LocalDateTime createdAt;

  /** Creates a ChatMessageResponse from a ChatMessage entity. */
  public static ChatMessageResponse fromEntity(ChatMessage message) {
    return ChatMessageResponse.builder()
        .id(message.getId())
        .role(message.getRole())
        .content(message.getContent())
        .modeUsed(message.getModeUsed())
        .tokenCount(message.getTokenCount())
        .createdAt(message.getCreatedAt())
        .build();
  }
}
