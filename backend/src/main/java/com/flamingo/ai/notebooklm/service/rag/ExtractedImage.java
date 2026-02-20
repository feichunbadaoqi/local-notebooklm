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
 */
public record ExtractedImage(
    int index,
    String mimeType,
    byte[] data,
    int width,
    int height,
    String altText,
    int approximateOffset) {}
