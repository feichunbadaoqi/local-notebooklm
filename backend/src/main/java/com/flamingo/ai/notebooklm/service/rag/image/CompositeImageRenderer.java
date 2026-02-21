package com.flamingo.ai.notebooklm.service.rag.image;

import com.flamingo.ai.notebooklm.service.rag.model.ExtractedImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

/**
 * Renders composite images from PDF page regions that contain multiple small images.
 *
 * <p>When PDFBox extracts individual icons/logos from a diagram, this service renders the entire
 * diagram region as a single image by capturing the bounding box that encompasses all grouped
 * images.
 */
@Service
@Slf4j
public class CompositeImageRenderer {

  private static final float DPI = 150f; // Render quality
  private static final float PADDING_RATIO = 0.05f; // 5% padding around bounding box

  /**
   * Renders a composite image from a PDF page region containing multiple grouped images.
   *
   * @param pdfStream input stream to the PDF document
   * @param groupedImages list of images in the spatial group (must be on same page)
   * @param compositeIndex index for the composite image
   * @return ExtractedImage with the rendered composite image data
   * @throws IOException if PDF rendering fails
   */
  public ExtractedImage renderComposite(
      InputStream pdfStream, List<ExtractedImage> groupedImages, int compositeIndex)
      throws IOException {

    if (groupedImages.isEmpty()) {
      throw new IllegalArgumentException("Cannot render composite from empty image group");
    }

    // Verify all images are on same page
    int pageNumber = groupedImages.get(0).pageNumber();
    if (groupedImages.stream().anyMatch(img -> img.pageNumber() != pageNumber)) {
      throw new IllegalArgumentException(
          "All images in group must be on same page for composite rendering");
    }

    byte[] pdfBytes = pdfStream.readAllBytes();
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      if (pageNumber < 0 || pageNumber >= document.getNumberOfPages()) {
        throw new IllegalArgumentException("Invalid page number: " + pageNumber);
      }

      // Calculate bounding box encompassing all images
      BoundingBox bbox = calculateBoundingBox(groupedImages, document, pageNumber);

      // Render the page region
      PDFRenderer renderer = new PDFRenderer(document);
      BufferedImage pageImage = renderer.renderImageWithDPI(pageNumber, DPI);

      // Crop to bounding box with padding
      int x = Math.max(0, bbox.x);
      int y = Math.max(0, bbox.y);
      int width = Math.min(bbox.width, pageImage.getWidth() - x);
      int height = Math.min(bbox.height, pageImage.getHeight() - y);

      BufferedImage croppedImage = pageImage.getSubimage(x, y, width, height);

      // Convert to PNG bytes
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(croppedImage, "png", baos);
      byte[] imageBytes = baos.toByteArray();

      // Calculate approximate offset (use first image's offset)
      int approximateOffset = groupedImages.get(0).approximateOffset();

      log.debug(
          "Rendered composite image {} from {} individual images on page {} ({}x{} pixels, {} KB)",
          compositeIndex,
          groupedImages.size(),
          pageNumber,
          width,
          height,
          imageBytes.length / 1024);

      return new ExtractedImage(
          compositeIndex,
          "image/png",
          imageBytes,
          width,
          height,
          String.format("Composite diagram from page %d", pageNumber + 1),
          approximateOffset,
          pageNumber,
          bbox.pdfX,
          bbox.pdfY,
          groupedImages.get(0).spatialGroupId());
    }
  }

  /**
   * Calculates the bounding box that encompasses all images in the group.
   *
   * @param images list of images to encompass
   * @param document PDF document
   * @param pageNumber page number
   * @return BoundingBox in pixel coordinates
   */
  private BoundingBox calculateBoundingBox(
      List<ExtractedImage> images, PDDocument document, int pageNumber) {

    // Get page dimensions in PDF units
    var page = document.getPage(pageNumber);
    float pageHeight = page.getMediaBox().getHeight();
    float pageWidth = page.getMediaBox().getWidth();

    // Find min/max coordinates in PDF units
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = Float.MIN_VALUE;
    float maxY = Float.MIN_VALUE;

    for (ExtractedImage img : images) {
      float x = img.xCoordinate();
      float y = img.yCoordinate();
      float imgWidth = img.width() > 0 ? img.width() : 50f; // Default size if unknown
      float imgHeight = img.height() > 0 ? img.height() : 50f;

      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x + imgWidth);
      maxY = Math.max(maxY, y + imgHeight);
    }

    // Add padding
    float padding = Math.max((maxX - minX) * PADDING_RATIO, (maxY - minY) * PADDING_RATIO);
    minX = Math.max(0, minX - padding);
    minY = Math.max(0, minY - padding);
    maxX = Math.min(pageWidth, maxX + padding);
    maxY = Math.min(pageHeight, maxY + padding);

    // Convert PDF coordinates to pixel coordinates (PDF origin is bottom-left, image is top-left)
    float scale = DPI / 72f; // PDF uses 72 DPI
    int pixelX = Math.round(minX * scale);
    int pixelY = Math.round((pageHeight - maxY) * scale); // Flip Y axis
    int pixelWidth = Math.round((maxX - minX) * scale);
    int pixelHeight = Math.round((maxY - minY) * scale);

    return new BoundingBox(pixelX, pixelY, pixelWidth, pixelHeight, minX, minY);
  }

  private record BoundingBox(int x, int y, int width, int height, float pdfX, float pdfY) {}
}
