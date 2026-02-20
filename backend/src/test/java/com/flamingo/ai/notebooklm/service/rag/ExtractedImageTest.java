package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link ExtractedImage}. */
class ExtractedImageTest {

  @Test
  void shouldCreateExtractedImage_withAllFields() {
    byte[] data = new byte[] {1, 2, 3};
    ExtractedImage image =
        new ExtractedImage(0, "image/png", data, 100, 100, "test", 500, 1, 150.0f, 200.0f, 5);

    assertThat(image.index()).isEqualTo(0);
    assertThat(image.mimeType()).isEqualTo("image/png");
    assertThat(image.data()).isEqualTo(data);
    assertThat(image.width()).isEqualTo(100);
    assertThat(image.height()).isEqualTo(100);
    assertThat(image.altText()).isEqualTo("test");
    assertThat(image.approximateOffset()).isEqualTo(500);
    assertThat(image.pageNumber()).isEqualTo(1);
    assertThat(image.xCoordinate()).isEqualTo(150.0f);
    assertThat(image.yCoordinate()).isEqualTo(200.0f);
    assertThat(image.spatialGroupId()).isEqualTo(5);
  }

  @Test
  void shouldCreateExtractedImage_withoutSpatialData() {
    byte[] data = new byte[] {1, 2, 3};
    ExtractedImage image =
        ExtractedImage.withoutSpatialData(0, "image/jpeg", data, 200, 150, "alt text", 1000);

    assertThat(image.index()).isEqualTo(0);
    assertThat(image.mimeType()).isEqualTo("image/jpeg");
    assertThat(image.data()).isEqualTo(data);
    assertThat(image.width()).isEqualTo(200);
    assertThat(image.height()).isEqualTo(150);
    assertThat(image.altText()).isEqualTo("alt text");
    assertThat(image.approximateOffset()).isEqualTo(1000);

    // Spatial fields should have default values
    assertThat(image.pageNumber()).isEqualTo(-1);
    assertThat(image.xCoordinate()).isEqualTo(0.0f);
    assertThat(image.yCoordinate()).isEqualTo(0.0f);
    assertThat(image.spatialGroupId()).isEqualTo(-1);
  }

  @Test
  void shouldMaintainBackwardCompatibility_withFactoryMethod() {
    byte[] data = new byte[] {4, 5, 6};

    // Factory method should work for non-PDF parsers
    ExtractedImage image1 = ExtractedImage.withoutSpatialData(1, "image/gif", data, 50, 50, "", 0);

    assertThat(image1.pageNumber()).isEqualTo(-1);
    assertThat(image1.spatialGroupId()).isEqualTo(-1);

    // Full constructor should work for PDF parser
    ExtractedImage image2 =
        new ExtractedImage(2, "image/png", data, 50, 50, "", 0, 0, 100.0f, 100.0f, -1);

    assertThat(image2.pageNumber()).isEqualTo(0);
    assertThat(image2.xCoordinate()).isEqualTo(100.0f);
  }
}
