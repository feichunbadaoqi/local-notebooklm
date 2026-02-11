package com.flamingo.ai.notebooklm.api.dto.request;

import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for sending a chat message. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

  @NotBlank(message = "Message is required")
  @Size(max = 10000, message = "Message must not exceed 10000 characters")
  private String message;

  /** Optional mode override for this message. If null, uses session's current mode. */
  private InteractionMode mode;
}
