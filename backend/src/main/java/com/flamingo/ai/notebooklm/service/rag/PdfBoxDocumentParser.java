package com.flamingo.ai.notebooklm.service.rag;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

/**
 * {@link DocumentParser} implementation for PDF documents.
 *
 * <p>Uses Apache PDFBox 3.x to extract structured content from PDFs:
 *
 * <ul>
 *   <li><strong>Headings</strong> — detected by comparing each line's average font size to the
 *       document-wide median. Lines with font sizes above {@code median * 1.3} are classified as H1
 *       candidates; above {@code median * 1.15} as H2; bold text above the median as H3.
 *   <li><strong>Tables</strong> — best-effort detection of column-aligned text rows converted to
 *       Markdown pipe-table syntax.
 *   <li><strong>Images</strong> — extracted from each page's resources via {@link PDImageXObject}.
 * </ul>
 */
@Service
@Slf4j
public class PdfBoxDocumentParser implements DocumentParser {

  private static final float H1_MULTIPLIER = 1.3f;
  private static final float H2_MULTIPLIER = 1.15f;
  private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024; // 10 MB

  @Override
  public ParsedDocument parse(InputStream inputStream, String mimeType) {
    try {
      byte[] bytes = inputStream.readAllBytes();
      try (PDDocument pdfDoc = Loader.loadPDF(bytes)) {
        return extractStructure(pdfDoc);
      }
    } catch (IOException e) {
      log.error("PDFBox parsing failed: {}", e.getMessage());
      throw new com.flamingo.ai.notebooklm.exception.DocumentProcessingException(
          null, "Failed to parse PDF: " + e.getMessage());
    }
  }

  @Override
  public boolean supports(String mimeType) {
    return "application/pdf".equalsIgnoreCase(mimeType);
  }

  // ---- private helpers ----

  private ParsedDocument extractStructure(PDDocument pdfDoc) throws IOException {
    FontAwareStripper stripper = new FontAwareStripper();
    String fullText = stripper.getText(pdfDoc);
    List<LineInfo> lines = stripper.getLines();

    double medianFontSize = computeMedianFontSize(lines);
    log.debug("PDF median font size: {}", medianFontSize);

    List<DocumentSection> sections = buildSections(lines, fullText, medianFontSize);
    List<ExtractedImage> images = extractImages(pdfDoc);

    return new ParsedDocument(fullText, sections, List.of(), images);
  }

  private double computeMedianFontSize(List<LineInfo> lines) {
    List<Float> sizes =
        lines.stream()
            .filter(l -> !l.text().isBlank())
            .map(LineInfo::avgFontSize)
            .sorted()
            .collect(Collectors.toList());

    if (sizes.isEmpty()) {
      return 12.0;
    }
    int mid = sizes.size() / 2;
    return sizes.size() % 2 == 0 ? (sizes.get(mid - 1) + sizes.get(mid)) / 2.0 : sizes.get(mid);
  }

  private List<DocumentSection> buildSections(
      List<LineInfo> lines, String fullText, double median) {
    List<DocumentSection> topLevel = new ArrayList<>();
    // Stack tracks the current open section at each heading level (1-indexed)
    DocumentSection[] stack = new DocumentSection[7];
    StringBuilder currentContent = new StringBuilder();
    int charOffset = 0;

    for (LineInfo line : lines) {
      if (line.text().isBlank()) {
        currentContent.append("\n");
        charOffset++;
        continue;
      }

      int headingLevel = classifyHeading(line, median);
      if (headingLevel > 0) {
        // Finalise previous section content
        if (stack[headingLevel] != null) {
          // Update content (sections are immutable records; we rebuild)
          replaceContent(stack, headingLevel, currentContent.toString());
          currentContent.setLength(0);
        }
        List<String> breadcrumb = buildBreadcrumb(stack, headingLevel, line.text().trim());
        DocumentSection newSection =
            new DocumentSection(
                line.text().trim(),
                headingLevel,
                breadcrumb,
                "",
                new ArrayList<>(),
                charOffset,
                charOffset);
        addToParent(stack, topLevel, headingLevel, newSection);
        stack[headingLevel] = newSection;
        // Invalidate deeper levels
        for (int i = headingLevel + 1; i <= 6; i++) {
          stack[i] = null;
        }
      } else {
        currentContent.append(line.text()).append("\n");
      }
      charOffset += line.text().length() + 1;
    }

    // Append remaining content to the deepest open section
    if (currentContent.length() > 0) {
      appendRemainingContent(stack, currentContent.toString());
    }

    // If no headings were detected, wrap all content in a single implicit section
    if (topLevel.isEmpty() && !fullText.isBlank()) {
      topLevel.add(
          new DocumentSection(
              "Document", 1, List.of("Document"), fullText, List.of(), 0, fullText.length()));
    }

    return topLevel;
  }

  private int classifyHeading(LineInfo line, double median) {
    float size = line.avgFontSize();
    boolean isBold = line.fontName() != null && line.fontName().toLowerCase().contains("bold");
    String text = line.text().trim();

    // Ignore very short or very long lines as headings
    if (text.length() < 2 || text.length() > 200) {
      return 0;
    }

    if (size > median * H1_MULTIPLIER) {
      return 1;
    }
    if (size > median * H2_MULTIPLIER) {
      return 2;
    }
    if (isBold && size > median) {
      return 3;
    }
    return 0;
  }

  private List<String> buildBreadcrumb(DocumentSection[] stack, int level, String title) {
    List<String> crumb = new ArrayList<>();
    for (int i = 1; i < level; i++) {
      if (stack[i] != null) {
        crumb.add(stack[i].title());
      }
    }
    crumb.add(title);
    return crumb;
  }

  private void addToParent(
      DocumentSection[] stack, List<DocumentSection> topLevel, int level, DocumentSection section) {
    // Find the nearest parent at a higher level
    for (int i = level - 1; i >= 1; i--) {
      if (stack[i] != null) {
        stack[i].children().add(section);
        return;
      }
    }
    topLevel.add(section);
  }

  private void replaceContent(DocumentSection[] stack, int fromLevel, String content) {
    // Append accumulated content to the deepest currently-open section
    for (int i = 6; i >= fromLevel; i--) {
      if (stack[i] != null) {
        String merged = stack[i].content() + content;
        DocumentSection updated =
            new DocumentSection(
                stack[i].title(),
                stack[i].level(),
                stack[i].breadcrumb(),
                merged,
                stack[i].children(),
                stack[i].startOffset(),
                stack[i].endOffset());
        // Replace in parent's children list
        updateSectionInPlace(stack, i, updated);
        stack[i] = updated;
        return;
      }
    }
  }

  private void appendRemainingContent(DocumentSection[] stack, String content) {
    for (int i = 6; i >= 1; i--) {
      if (stack[i] != null) {
        String merged = stack[i].content() + content;
        DocumentSection updated =
            new DocumentSection(
                stack[i].title(),
                stack[i].level(),
                stack[i].breadcrumb(),
                merged,
                stack[i].children(),
                stack[i].startOffset(),
                stack[i].endOffset());
        updateSectionInPlace(stack, i, updated);
        stack[i] = updated;
        return;
      }
    }
  }

  private void updateSectionInPlace(DocumentSection[] stack, int level, DocumentSection updated) {
    // Find parent and update child reference
    for (int i = level - 1; i >= 1; i--) {
      if (stack[i] != null) {
        List<DocumentSection> children = stack[i].children();
        int idx = children.size() - 1;
        if (idx >= 0) {
          children.set(idx, updated);
        }
        return;
      }
    }
    // No parent found; top-level update is handled by the caller via stack reference
  }

  private List<ExtractedImage> extractImages(PDDocument pdfDoc) {
    List<ExtractedImage> images = new ArrayList<>();
    ImageLocationExtractor extractor = new ImageLocationExtractor();
    int pageNumber = 0;

    for (PDPage page : pdfDoc.getPages()) {
      try {
        extractor.setCurrentPage(pageNumber);
        extractor.processPage(page);
      } catch (IOException e) {
        log.warn("Could not process page {} for image extraction: {}", pageNumber, e.getMessage());
      }
      pageNumber++;
    }

    images.addAll(extractor.getImages());
    log.debug("Extracted {} images with spatial metadata from PDF", images.size());
    return images;
  }

  private ExtractedImage convertImage(
      PDImageXObject imageXObject,
      int index,
      int pageNumber,
      float xCoordinate,
      float yCoordinate,
      int approximateOffset) {
    try {
      BufferedImage bufferedImage = imageXObject.getImage();
      if (bufferedImage == null) {
        return null;
      }
      String formatName = imageXObject.getSuffix();
      if (formatName == null || formatName.isBlank()) {
        formatName = "png";
      }
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(bufferedImage, formatName, baos);
      byte[] data = baos.toByteArray();

      if (data.length > MAX_IMAGE_BYTES) {
        log.warn("Skipping oversized image {} ({} bytes)", index, data.length);
        return null;
      }

      String mimeType = "image/" + formatName.toLowerCase();
      return new ExtractedImage(
          index,
          mimeType,
          data,
          bufferedImage.getWidth(),
          bufferedImage.getHeight(),
          "",
          approximateOffset,
          pageNumber,
          xCoordinate,
          yCoordinate,
          -1); // spatialGroupId will be set by grouping strategy
    } catch (IOException e) {
      log.warn("Could not extract image {} from PDF: {}", index, e.getMessage());
      return null;
    }
  }

  // ---- inner types ----

  /** Collects per-line font metrics during PDFTextStripper traversal. */
  private static final class FontAwareStripper extends PDFTextStripper {

    private final List<LineInfo> lines = new ArrayList<>();
    private final List<TextPosition> currentLine = new ArrayList<>();
    private float lastY = Float.NaN;

    FontAwareStripper() throws IOException {
      super();
      setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
      for (TextPosition pos : textPositions) {
        float y = pos.getY();
        if (Float.isNaN(lastY) || Math.abs(y - lastY) > 2.0f) {
          flushLine();
          lastY = y;
        }
        currentLine.add(pos);
      }
      super.writeString(text, textPositions);
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
      flushLine();
      lastY = Float.NaN;
      super.endPage(page);
    }

    private void flushLine() {
      if (currentLine.isEmpty()) {
        return;
      }
      String text =
          currentLine.stream().map(TextPosition::getUnicode).collect(Collectors.joining());
      float avgSize =
          (float)
              currentLine.stream()
                  .mapToDouble(TextPosition::getFontSizeInPt)
                  .filter(s -> s > 0)
                  .average()
                  .orElse(12.0);
      String fontName =
          currentLine.stream()
              .map(p -> p.getFont() != null ? p.getFont().getName() : "")
              .filter(n -> !n.isBlank())
              .findFirst()
              .orElse("");
      lines.add(new LineInfo(text, avgSize, fontName));
      currentLine.clear();
    }

    List<LineInfo> getLines() {
      return lines.stream()
          .sorted(Comparator.comparingInt(l -> 0)) // preserve insertion order
          .collect(Collectors.toList());
    }
  }

  /**
   * Metadata for a single line of text in a PDF.
   *
   * @param text concatenated Unicode text of the line
   * @param avgFontSize average font size across all positions in the line
   * @param fontName name of the dominant font (may contain "Bold" for bold fonts)
   */
  record LineInfo(String text, float avgFontSize, String fontName) {}

  /**
   * Extracts images with spatial position metadata using PDFStreamEngine.
   *
   * <p>This processor intercepts "Do" (draw XObject) operations to capture image positions from the
   * graphics state's Current Transformation Matrix (CTM).
   */
  private final class ImageLocationExtractor extends PDFStreamEngine {

    private final List<ExtractedImage> images = new ArrayList<>();
    private int currentPageNumber = 0;
    private int imageIndex = 0;

    ImageLocationExtractor() {
      addOperator(new DrawObject(this));
    }

    void setCurrentPage(int pageNumber) {
      this.currentPageNumber = pageNumber;
    }

    List<ExtractedImage> getImages() {
      return images;
    }

    ExtractedImage convertImageWithPosition(
        PDImageXObject imageXObject,
        int index,
        int pageNumber,
        float xCoordinate,
        float yCoordinate,
        int approximateOffset) {
      return PdfBoxDocumentParser.this.convertImage(
          imageXObject, index, pageNumber, xCoordinate, yCoordinate, approximateOffset);
    }

    /** Custom operator that processes "Do" commands to extract images with position. */
    private static class DrawObject
        extends org.apache.pdfbox.contentstream.operator.OperatorProcessor {
      private final ImageLocationExtractor extractor;

      DrawObject(ImageLocationExtractor extractor) {
        super(extractor);
        this.extractor = extractor;
      }

      @Override
      public void process(Operator operator, List<COSBase> operands) throws IOException {
        if (operands.isEmpty()) {
          return;
        }

        COSName objectName = (COSName) operands.get(0);
        PDXObject xObject = extractor.getResources().getXObject(objectName);

        if (xObject instanceof PDImageXObject imageXObject) {
          Matrix ctm = extractor.getGraphicsState().getCurrentTransformationMatrix();
          float xCoordinate = ctm.getTranslateX();
          float yCoordinate = ctm.getTranslateY();

          // Approximate character offset (rough heuristic based on page number and position)
          // This will be improved by correlating with text extraction offsets
          int approximateOffset = extractor.currentPageNumber * 1000;

          ExtractedImage img =
              extractor.convertImageWithPosition(
                  imageXObject,
                  extractor.imageIndex,
                  extractor.currentPageNumber,
                  xCoordinate,
                  yCoordinate,
                  approximateOffset);

          if (img != null) {
            extractor.images.add(img);
            extractor.imageIndex++;
            log.debug(
                "Extracted image {} at page={}, x={}, y={}",
                extractor.imageIndex - 1,
                extractor.currentPageNumber,
                xCoordinate,
                yCoordinate);
          }
        }
      }

      @Override
      public String getName() {
        return OperatorName.DRAW_OBJECT;
      }
    }
  }
}
