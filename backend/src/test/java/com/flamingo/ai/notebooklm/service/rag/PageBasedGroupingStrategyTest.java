package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.config.RagConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PageBasedGroupingStrategy}. */
class PageBasedGroupingStrategyTest {

  private RagConfig ragConfig;
  private PageBasedGroupingStrategy strategy;

  @BeforeEach
  void setUp() {
    ragConfig = mock(RagConfig.class);
    RagConfig.ImageGrouping imageGrouping = new RagConfig.ImageGrouping();
    imageGrouping.getPageBased().setMinGroupSize(2);

    when(ragConfig.getImageGrouping()).thenReturn(imageGrouping);

    strategy = new PageBasedGroupingStrategy(ragConfig);
  }

  @Test
  void shouldGroupAllImages_onSamePage() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 0.0f, 0.0f),
            createImage(1, 0, 500.0f, 500.0f), // Far apart but same page
            createImage(2, 0, 1000.0f, 1000.0f));

    List<ExtractedImage> result = strategy.groupImages(images);

    // All images on page 0 should be in the same group
    assertThat(result.get(0).spatialGroupId()).isEqualTo(result.get(1).spatialGroupId());
    assertThat(result.get(1).spatialGroupId()).isEqualTo(result.get(2).spatialGroupId());
    assertThat(result.get(0).spatialGroupId()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void shouldSeparateGroups_onDifferentPages() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 100.0f, 100.0f),
            createImage(1, 0, 200.0f, 200.0f), // Page 0
            createImage(2, 1, 100.0f, 100.0f),
            createImage(3, 1, 200.0f, 200.0f)); // Page 1

    List<ExtractedImage> result = strategy.groupImages(images);

    // Images 0-1 (page 0) should be grouped
    assertThat(result.get(0).spatialGroupId()).isEqualTo(result.get(1).spatialGroupId());
    assertThat(result.get(0).spatialGroupId()).isGreaterThanOrEqualTo(0);

    // Images 2-3 (page 1) should be grouped
    assertThat(result.get(2).spatialGroupId()).isEqualTo(result.get(3).spatialGroupId());
    assertThat(result.get(2).spatialGroupId()).isGreaterThanOrEqualTo(0);

    // Groups should be different
    assertThat(result.get(0).spatialGroupId()).isNotEqualTo(result.get(2).spatialGroupId());
  }

  @Test
  void shouldNotGroup_singleImageOnPage() {
    List<ExtractedImage> images = List.of(createImage(0, 0, 100.0f, 100.0f));

    List<ExtractedImage> result = strategy.groupImages(images);

    // Single image should remain ungrouped (below minGroupSize)
    assertThat(result.get(0).spatialGroupId()).isEqualTo(-1);
  }

  @Test
  void shouldIgnoreNonPdfImages() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, -1, 0.0f, 0.0f), // Non-PDF (pageNumber = -1)
            createImage(1, -1, 100.0f, 100.0f));

    List<ExtractedImage> result = strategy.groupImages(images);

    // Non-PDF images should remain ungrouped
    assertThat(result.get(0).spatialGroupId()).isEqualTo(-1);
    assertThat(result.get(1).spatialGroupId()).isEqualTo(-1);
  }

  @Test
  void shouldHandleMixedPdfAndNonPdfImages() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 100.0f, 100.0f),
            createImage(1, 0, 200.0f, 200.0f), // PDF images on page 0
            createImage(2, -1, 0.0f, 0.0f)); // Non-PDF image

    List<ExtractedImage> result = strategy.groupImages(images);

    // PDF images should be grouped
    assertThat(result.get(0).spatialGroupId()).isEqualTo(result.get(1).spatialGroupId());
    assertThat(result.get(0).spatialGroupId()).isGreaterThanOrEqualTo(0);

    // Non-PDF image should remain ungrouped
    assertThat(result.get(2).spatialGroupId()).isEqualTo(-1);
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
    imageGrouping.getPageBased().setMinGroupSize(3);
    when(ragConfig.getImageGrouping()).thenReturn(imageGrouping);
    strategy = new PageBasedGroupingStrategy(ragConfig);

    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 0.0f, 0.0f),
            createImage(1, 0, 100.0f, 100.0f)); // Only 2 images on page

    List<ExtractedImage> result = strategy.groupImages(images);

    // Page has only 2 images (< minGroupSize=3), so should remain ungrouped
    assertThat(result.get(0).spatialGroupId()).isEqualTo(-1);
    assertThat(result.get(1).spatialGroupId()).isEqualTo(-1);
  }

  @Test
  void shouldAssignUniqueGroupIds_forDifferentPages() {
    List<ExtractedImage> images =
        List.of(
            createImage(0, 0, 0.0f, 0.0f),
            createImage(1, 0, 100.0f, 100.0f), // Page 0
            createImage(2, 1, 0.0f, 0.0f),
            createImage(3, 1, 100.0f, 100.0f), // Page 1
            createImage(4, 2, 0.0f, 0.0f),
            createImage(5, 2, 100.0f, 100.0f)); // Page 2

    List<ExtractedImage> result = strategy.groupImages(images);

    int group0 = result.get(0).spatialGroupId();
    int group1 = result.get(2).spatialGroupId();
    int group2 = result.get(4).spatialGroupId();

    // All three groups should have unique IDs
    assertThat(group0).isNotEqualTo(group1);
    assertThat(group1).isNotEqualTo(group2);
    assertThat(group0).isNotEqualTo(group2);

    // Each group should have 2 images
    assertThat(result.get(0).spatialGroupId()).isEqualTo(result.get(1).spatialGroupId());
    assertThat(result.get(2).spatialGroupId()).isEqualTo(result.get(3).spatialGroupId());
    assertThat(result.get(4).spatialGroupId()).isEqualTo(result.get(5).spatialGroupId());
  }

  @Test
  void shouldReturnCorrectStrategyName() {
    assertThat(strategy.getStrategyName()).isEqualTo("PageBasedGroupingStrategy");
  }

  // Helper method to create test images
  private ExtractedImage createImage(int index, int pageNumber, float x, float y) {
    byte[] data = new byte[] {1, 2, 3};
    return new ExtractedImage(index, "image/png", data, 100, 100, "", 0, pageNumber, x, y, -1);
  }
}
