package com.flamingo.ai.notebooklm.api.rest;

import com.flamingo.ai.notebooklm.domain.entity.DocumentImage;
import com.flamingo.ai.notebooklm.domain.repository.DocumentImageRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for serving extracted document images.
 *
 * <p>Validates that the requested image belongs to the specified session before serving bytes.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

  private final DocumentImageRepository imageRepository;

  /**
   * Serves an extracted image as binary data.
   *
   * @param sessionId session the image belongs to (used for access validation)
   * @param imageId UUID of the {@link DocumentImage} entity
   * @return image bytes with correct Content-Type, or 404 if not found / session mismatch
   */
  @GetMapping("/{imageId}")
  public ResponseEntity<byte[]> getImage(@PathVariable UUID sessionId, @PathVariable UUID imageId) {
    return imageRepository
        .findById(imageId)
        .filter(img -> img.getSessionId().equals(sessionId))
        .map(img -> readImageBytes(img))
        .orElse(ResponseEntity.notFound().build());
  }

  private ResponseEntity<byte[]> readImageBytes(DocumentImage image) {
    try {
      Path filePath = Path.of(image.getFilePath());
      if (!Files.exists(filePath)) {
        log.warn("Image file not found on disk: {}", filePath);
        return ResponseEntity.notFound().build();
      }
      byte[] bytes = Files.readAllBytes(filePath);
      MediaType mediaType = parseMediaType(image.getMimeType());
      return ResponseEntity.ok().contentType(mediaType).body(bytes);
    } catch (IOException e) {
      log.error("Failed to read image file for imageId={}: {}", image.getId(), e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private MediaType parseMediaType(String mimeType) {
    try {
      return MediaType.parseMediaType(mimeType);
    } catch (Exception e) {
      return MediaType.IMAGE_PNG;
    }
  }
}
