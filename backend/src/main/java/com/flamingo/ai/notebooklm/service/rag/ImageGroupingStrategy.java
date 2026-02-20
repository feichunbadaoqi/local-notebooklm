package com.flamingo.ai.notebooklm.service.rag;

import java.util.List;

/**
 * Strategy for grouping related images to keep them in the same document chunk.
 *
 * <p>Implementations of this interface analyze spatial metadata (page number, coordinates) to
 * identify images that should be kept together during the chunking process, such as icons in an
 * architecture diagram.
 *
 * <p>The strategy assigns a {@code spatialGroupId} to related images. Images with the same
 * non-negative group ID will be associated with the same text chunk.
 */
public interface ImageGroupingStrategy {

  /**
   * Groups related images by assigning spatial group IDs.
   *
   * <p>Images that should be kept together receive the same {@code spatialGroupId} (0 or higher).
   * Ungrouped images retain {@code spatialGroupId = -1}.
   *
   * @param images list of extracted images with spatial metadata (pageNumber, xCoordinate,
   *     yCoordinate)
   * @return list of images with updated {@code spatialGroupId} field
   */
  List<ExtractedImage> groupImages(List<ExtractedImage> images);

  /**
   * Returns a human-readable name of this strategy for logging and debugging.
   *
   * @return strategy name (e.g., "SpatialClusteringStrategy", "PageBasedGroupingStrategy")
   */
  String getStrategyName();
}
