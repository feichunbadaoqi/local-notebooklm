package com.flamingo.ai.notebooklm.service.rag;

/**
 * An image embedded in a document, with its raw bytes and descriptive metadata.
 *
 * @param index sequential index of this image within the document (0-based)
 * @param mimeType MIME type of the image data (e.g. {@code image/png}, {@code image/jpeg})
 * @param data raw image bytes
 * @param width image width in pixels (0 if unknown)
 * @param height image height in pixels (0 if unknown)
 * @param altText alt-text or caption extracted from the surrounding document structure
 * @param approximateOffset character offset in the full document text (best-effort)
 * @param pageNumber 0-based page number for PDF images (-1 for non-PDF or unknown)
 * @param xCoordinate X position in PDF coordinate units (0.0 for non-PDF or unknown)
 * @param yCoordinate Y position in PDF coordinate units (0.0 for non-PDF or unknown)
 * @param spatialGroupId group ID for spatially-related images (-1 for ungrouped, 0+ for grouped)
 */
public record ExtractedImage(
    int index,
    String mimeType,
    byte[] data,
    int width,
    int height,
    String altText,
    int approximateOffset,
    int pageNumber,
    float xCoordinate,
    float yCoordinate,
    int spatialGroupId) {

  /**
   * Factory method for parsers that don't support spatial metadata (non-PDF parsers).
   *
   * @param index sequential index of this image
   * @param mimeType MIME type
   * @param data raw image bytes
   * @param width image width in pixels
   * @param height image height in pixels
   * @param altText alt-text or caption
   * @param approximateOffset character offset
   * @return ExtractedImage with spatial fields set to default values
   */
  public static ExtractedImage withoutSpatialData(
      int index,
      String mimeType,
      byte[] data,
      int width,
      int height,
      String altText,
      int approximateOffset) {
    return new ExtractedImage(
        index, mimeType, data, width, height, altText, approximateOffset, -1, 0f, 0f, -1);
  }
}
