package com.flamingo.ai.notebooklm.service.rag.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.service.rag.model.ExtractedImage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SpatialClusteringStrategy}. */
class SpatialClusteringStrategyTest {

  private RagConfig ragConfig;
  private SpatialClusteringStrategy strategy;

  @BeforeEach
  void setUp() {
    ragConfig = mock(RagConfig.class);
    RagConfig.ImageGrouping imageGrouping = new RagConfig.ImageGrouping();
    imageGrouping.getSpatial().setThreshold(100.0f);
    imageGrouping.getSpatial().setMinGroupSize(2);

    when(ragConfig.getImageGrouping()).thenReturn(imageGrouping);

    strategy = new SpatialClusteringStrategy(ragConfig);
  }

  @Test
  void shouldGroupCloseImages_onSamePage() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 50.0f, 50.0f), // Close to image 1
            createImage(1, 0, 60.0f, 55.0f), // Close to image 0
            createImage(2, 0, 500.0f, 500.0f)); // Far from others

    List<ExtractedImage> result = strategy.groupImages(images);

    // Images 0 and 1 should be grouped together
    assertThat(result.get(0).spatialGroupId()).isEqualTo(result.get(1).spatialGroupId());
    assertThat(result.get(0).spatialGroupId()).isGreaterThanOrEqualTo(0);

    // Image 2 should remain ungrouped
    assertThat(result.get(2).spatialGroupId()).isEqualTo(-1);
  }

  @Test
  void shouldNotGroup_distantImages() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 0.0f, 0.0f), createImage(1, 0, 200.0f, 200.0f)); // Beyond threshold

    List<ExtractedImage> result = strategy.groupImages(images);

    // Both images should remain ungrouped (distance > threshold)
    assertThat(result.get(0).spatialGroupId()).isEqualTo(-1);
    assertThat(result.get(1).spatialGroupId()).isEqualTo(-1);
  }

  @Test
  void shouldGroupTransitively_threeImages() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 0.0f, 0.0f),
            createImage(1, 0, 50.0f, 50.0f), // Close to 0
            createImage(2, 0, 100.0f, 100.0f)); // Close to 1, far from 0 (but transitive)

    List<ExtractedImage> result = strategy.groupImages(images);

    // All three should be in the same group (transitive closure)
    assertThat(result.get(0).spatialGroupId()).isEqualTo(result.get(1).spatialGroupId());
    assertThat(result.get(1).spatialGroupId()).isEqualTo(result.get(2).spatialGroupId());
    assertThat(result.get(0).spatialGroupId()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void shouldSeparateGroups_onDifferentPages() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 50.0f, 50.0f),
            createImage(1, 0, 60.0f, 55.0f), // Same page, close
            createImage(2, 1, 50.0f, 50.0f),
            createImage(3, 1, 60.0f, 55.0f)); // Different page, close

    List<ExtractedImage> result = strategy.groupImages(images);

    // Images 0-1 should be grouped
    assertThat(result.get(0).spatialGroupId()).isEqualTo(result.get(1).spatialGroupId());
    assertThat(result.get(0).spatialGroupId()).isGreaterThanOrEqualTo(0);

    // Images 2-3 should be grouped
    assertThat(result.get(2).spatialGroupId()).isEqualTo(result.get(3).spatialGroupId());
    assertThat(result.get(2).spatialGroupId()).isGreaterThanOrEqualTo(0);

    // Groups should be different
    assertThat(result.get(0).spatialGroupId()).isNotEqualTo(result.get(2).spatialGroupId());
  }

  @Test
  void shouldIgnoreNonPdfImages() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, -1, 0.0f, 0.0f), // Non-PDF (pageNumber = -1)
            createImage(1, -1, 10.0f, 10.0f)); // Non-PDF

    List<ExtractedImage> result = strategy.groupImages(images);

    // Non-PDF images should remain ungrouped
    assertThat(result.get(0).spatialGroupId()).isEqualTo(-1);
    assertThat(result.get(1).spatialGroupId()).isEqualTo(-1);
  }

  @Test
  void shouldNotGroupSingleImage() {
    List<ExtractedImage> images = List.of(createImage(0, 0, 100.0f, 100.0f));

    List<ExtractedImage> result = strategy.groupImages(images);

    // Single image should remain ungrouped (below minGroupSize)
    assertThat(result.get(0).spatialGroupId()).isEqualTo(-1);
  }

  @Test
  void shouldHandleEmptyList() {
    List<ExtractedImage> result = strategy.groupImages(List.of());

    assertThat(result).isEmpty();
  }

  @Test
  void shouldHandleNullList() {
    List<ExtractedImage> result = strategy.groupImages(null);

    assertThat(result).isNull();
  }

  @Test
  void shouldRespectMinGroupSize() {
    // Set minGroupSize to 3
    RagConfig.ImageGrouping imageGrouping = new RagConfig.ImageGrouping();
    imageGrouping.getSpatial().setThreshold(100.0f);
    imageGrouping.getSpatial().setMinGroupSize(3);
    when(ragConfig.getImageGrouping()).thenReturn(imageGrouping);
    strategy = new SpatialClusteringStrategy(ragConfig);

    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 0.0f, 0.0f), createImage(1, 0, 10.0f, 10.0f)); // Only 2 images close

    List<ExtractedImage> result = strategy.groupImages(images);

    // Group has only 2 images (< minGroupSize=3), so should remain ungrouped
    assertThat(result.get(0).spatialGroupId()).isEqualTo(-1);
    assertThat(result.get(1).spatialGroupId()).isEqualTo(-1);
  }

  @Test
  void shouldAssignUniqueGroupIds() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 0.0f, 0.0f),
            createImage(1, 0, 10.0f, 10.0f),
            createImage(2, 0, 500.0f, 500.0f),
            createImage(3, 0, 510.0f, 510.0f));

    List<ExtractedImage> result = strategy.groupImages(images);

    // Should have 2 groups with different IDs
    Map<Integer, Long> groupCounts =
        result.stream()
            .filter(img -> img.spatialGroupId() >= 0)
            .collect(Collectors.groupingBy(ExtractedImage::spatialGroupId, Collectors.counting()));

    assertThat(groupCounts).hasSize(2); // 2 distinct groups
    assertThat(groupCounts.values()).allMatch(count -> count == 2); // Each group has 2 images
  }

  @Test
  void shouldReturnCorrectStrategyName() {
    assertThat(strategy.getStrategyName()).isEqualTo("SpatialClusteringStrategy");
  }

  // Helper method to create test images
  private ExtractedImage createImage(int index, int pageNumber, float x, float y) {
    byte[] data = new byte[] {1, 2, 3};
    return new ExtractedImage(index, "image/png", data, 100, 100, "", 0, pageNumber, x, y, -1);
  }
}
