package com.flamingo.ai.notebooklm.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for creating a memory manually. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMemoryRequest {

  @NotBlank(message = "Content is required")
  @Size(max = 1000, message = "Content must not exceed 1000 characters")
  private String content;

  @NotBlank(message = "Type is required")
  @Pattern(
      regexp = "^(fact|preference|insight)$",
      message = "Type must be fact, preference, or insight")
  private String type;

  @Min(value = 0, message = "Importance must be at least 0.0")
  @Max(value = 1, message = "Importance must be at most 1.0")
  @Builder.Default
  private Float importance = 0.5f;
}
