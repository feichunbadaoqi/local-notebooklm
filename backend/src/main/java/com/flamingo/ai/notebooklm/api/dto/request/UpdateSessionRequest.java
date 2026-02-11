package com.flamingo.ai.notebooklm.api.dto.request;

import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for updating a session. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSessionRequest {

  @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
  private String title;

  private InteractionMode mode;
}
