package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.DocumentImage;
import com.flamingo.ai.notebooklm.domain.repository.DocumentImageRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for storing document images with support for composite rendering of spatial groups.
 *
 * <p>When multiple small images are spatially close (e.g., icons in a diagram), this service can
 * render them as a single composite image from the PDF, matching what users see in the original
 * document.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageStorageService {

  private final DocumentImageRepository documentImageRepository;
  private final CompositeImageRenderer compositeImageRenderer;
  private final ImageGroupingStrategy imageGroupingStrategy;
  private final RagConfig ragConfig;

  /**
   * Stores images with composite rendering for spatial groups.
   *
   * @param images extracted images from document
   * @param documentBytes original document bytes (for composite rendering)
   * @param documentId document UUID
   * @param sessionId session UUID
   * @return mapping from original image index to persisted entity UUID
   */
  public Map<Integer, String> storeImages(
      List<ExtractedImage> images, byte[] documentBytes, UUID documentId, UUID sessionId) {

    Map<Integer, String> indexToId = new HashMap<>();
    if (images.isEmpty() || !ragConfig.getImageStorage().isEnabled()) {
      return indexToId;
    }

    boolean isPdf = images.stream().anyMatch(img -> img.pageNumber() >= 0);

    // For PDFs with spatial metadata, use composite rendering
    if (isPdf && ragConfig.getImageStorage().isCompositeRenderingEnabled()) {
      return storeWithCompositeRendering(images, documentBytes, documentId, sessionId);
    }

    // Fallback: store images individually
    return storeIndividually(images, documentId, sessionId);
  }

  /**
   * Stores images with composite rendering for spatial groups.
   *
   * @return mapping from original image index to persisted entity UUID
   */
  private Map<Integer, String> storeWithCompositeRendering(
      List<ExtractedImage> images, byte[] documentBytes, UUID documentId, UUID sessionId) {

    Map<Integer, String> indexToId = new HashMap<>();

    // Group images
    List<ExtractedImage> groupedImages = imageGroupingStrategy.groupImages(images);

    // Partition into groups
    Map<Integer, List<ExtractedImage>> spatialGroups = new LinkedHashMap<>();
    List<ExtractedImage> ungroupedImages = new ArrayList<>();

    for (ExtractedImage img : groupedImages) {
      if (img.spatialGroupId() >= 0) {
        spatialGroups.computeIfAbsent(img.spatialGroupId(), k -> new ArrayList<>()).add(img);
      } else {
        ungroupedImages.add(img);
      }
    }

    int compositeIndex = 0;

    // Render and store composite images for each group
    for (Map.Entry<Integer, List<ExtractedImage>> entry : spatialGroups.entrySet()) {
      List<ExtractedImage> group = entry.getValue();

      try {
        // Render composite image
        ExtractedImage composite =
            compositeImageRenderer.renderComposite(
                new java.io.ByteArrayInputStream(documentBytes), group, compositeIndex);

        // Store composite image
        String compositeId = storeSingleImage(composite, documentId, sessionId);

        // Map all original image indices in this group to the composite ID
        for (ExtractedImage originalImg : group) {
          indexToId.put(originalImg.index(), compositeId);
        }

        compositeIndex++;
        log.debug(
            "Stored composite image for group {} ({} images) -> {}",
            entry.getKey(),
            group.size(),
            compositeId);

      } catch (IOException e) {
        log.warn(
            "Failed to render composite for group {}, falling back to individual storage: {}",
            entry.getKey(),
            e.getMessage());
        // Fallback: store individual images
        for (ExtractedImage img : group) {
          String id = storeSingleImage(img, documentId, sessionId);
          if (id != null) {
            indexToId.put(img.index(), id);
          }
        }
      }
    }

    // Store ungrouped images individually
    for (ExtractedImage img : ungroupedImages) {
      String id = storeSingleImage(img, documentId, sessionId);
      if (id != null) {
        indexToId.put(img.index(), id);
      }
    }

    log.info(
        "Stored {} composite images and {} individual images for document {}",
        spatialGroups.size(),
        ungroupedImages.size(),
        documentId);

    return indexToId;
  }

  /**
   * Stores images individually without composite rendering.
   *
   * @return mapping from image index to persisted entity UUID
   */
  private Map<Integer, String> storeIndividually(
      List<ExtractedImage> images, UUID documentId, UUID sessionId) {

    Map<Integer, String> indexToId = new HashMap<>();

    for (ExtractedImage image : images) {
      String id = storeSingleImage(image, documentId, sessionId);
      if (id != null) {
        indexToId.put(image.index(), id);
      }
    }

    return indexToId;
  }

  /**
   * Stores a single image to disk and database.
   *
   * @return UUID of stored image, or null if storage failed
   */
  private String storeSingleImage(ExtractedImage image, UUID documentId, UUID sessionId) {
    if (image.data() == null || image.data().length == 0) {
      return null;
    }

    long maxBytes = ragConfig.getImageStorage().getMaxFileSizeBytes();
    if (image.data().length > maxBytes) {
      log.warn(
          "Skipping oversized image {} ({} bytes) for document {}",
          image.index(),
          image.data().length,
          documentId);
      return null;
    }

    String basePath = ragConfig.getImageStorage().getBasePath();
    String extension = extensionForMimeType(image.mimeType());
    Path dir = Path.of(basePath, sessionId.toString(), documentId.toString());
    Path filePath = dir.resolve(image.index() + "." + extension);

    try {
      Files.createDirectories(dir);
      Files.write(filePath, image.data());

      DocumentImage entity =
          DocumentImage.builder()
              .documentId(documentId)
              .sessionId(sessionId)
              .imageIndex(image.index())
              .mimeType(image.mimeType())
              .altText(image.altText() != null ? image.altText() : "")
              .filePath(filePath.toAbsolutePath().toString())
              .width(image.width())
              .height(image.height())
              .build();

      DocumentImage saved = documentImageRepository.save(entity);
      log.debug("Stored image {} for document {} at {}", image.index(), documentId, filePath);
      return saved.getId().toString();

    } catch (IOException e) {
      log.warn(
          "Failed to store image {} for document {}: {}",
          image.index(),
          documentId,
          e.getMessage());
      return null;
    }
  }

  private String extensionForMimeType(String mimeType) {
    if (mimeType == null) {
      return "png";
    }
    return switch (mimeType.toLowerCase()) {
      case "image/jpeg", "image/jpg" -> "jpg";
      case "image/gif" -> "gif";
      case "image/webp" -> "webp";
      default -> "png";
    };
  }
}
