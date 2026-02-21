package com.flamingo.ai.notebooklm.service.rag.parsing;

import com.flamingo.ai.notebooklm.service.rag.model.DocumentSection;
import com.flamingo.ai.notebooklm.service.rag.model.ExtractedTable;
import com.flamingo.ai.notebooklm.service.rag.model.ParsedDocument;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * {@link DocumentParser} implementation for Office documents (DOCX, PPTX, XLSX, ODF, …).
 *
 * <p>Uses Apache Tika's {@link AutoDetectParser} with a {@link ToXMLContentHandler} to produce an
 * XHTML representation, then walks the DOM to extract:
 *
 * <ul>
 *   <li>{@code <h1>}–{@code <h6>} elements → {@link DocumentSection} hierarchy
 *   <li>{@code <table>} elements → Markdown pipe-table via {@link #tableToMarkdown}
 *   <li>All other text → section content paragraphs
 * </ul>
 */
@Service
@Slf4j
public class TikaXhtmlDocumentParser implements DocumentParser {

  private static final Set<String> SUPPORTED_MIME_PREFIXES =
      Set.of(
          "application/vnd.openxmlformats",
          "application/vnd.ms-",
          "application/msword",
          "application/vnd.oasis",
          "text/plain");

  @Override
  public ParsedDocument parse(InputStream inputStream, String mimeType) {
    try {
      byte[] xhtmlBytes = toXhtml(inputStream, mimeType);
      return parseXhtml(xhtmlBytes);
    } catch (Exception e) {
      log.error("TikaXhtmlDocumentParser failed for mimeType={}: {}", mimeType, e.getMessage());
      throw new com.flamingo.ai.notebooklm.exception.DocumentProcessingException(
          null, "Failed to parse document: " + e.getMessage());
    }
  }

  @Override
  public boolean supports(String mimeType) {
    if (mimeType == null) {
      return false;
    }
    return SUPPORTED_MIME_PREFIXES.stream().anyMatch(mimeType::startsWith)
        || "text/plain".equalsIgnoreCase(mimeType);
  }

  // ---- private helpers ----

  private byte[] toXhtml(InputStream inputStream, String mimeType) throws Exception {
    AutoDetectParser tikaParser = new AutoDetectParser();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ToXMLContentHandler handler = new ToXMLContentHandler(out, StandardCharsets.UTF_8.name());
    Metadata metadata = new Metadata();
    if (mimeType != null) {
      metadata.set(Metadata.CONTENT_TYPE, mimeType);
    }
    tikaParser.parse(inputStream, handler, metadata);
    return out.toByteArray();
  }

  private ParsedDocument parseXhtml(byte[] xhtmlBytes)
      throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    dbf.setNamespaceAware(true);
    org.w3c.dom.Document dom = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xhtmlBytes));
    dom.getDocumentElement().normalize();

    List<DocumentSection> topLevel = new ArrayList<>();
    List<ExtractedTable> tables = new ArrayList<>();
    DocumentSection[] stack = new DocumentSection[7];
    StringBuilder currentContent = new StringBuilder();
    int[] tableIndex = {0};
    int[] charOffset = {0};

    walkBody(
        dom.getDocumentElement(), topLevel, tables, stack, currentContent, tableIndex, charOffset);

    // Append any trailing content
    if (currentContent.length() > 0) {
      appendContent(stack, currentContent.toString());
    }

    // Build full text
    StringBuilder fullText = new StringBuilder();
    collectText(dom.getDocumentElement(), fullText);

    if (topLevel.isEmpty() && fullText.length() > 0) {
      topLevel.add(
          new DocumentSection(
              "Document",
              1,
              List.of("Document"),
              fullText.toString(),
              List.of(),
              0,
              fullText.length()));
    }

    return new ParsedDocument(fullText.toString(), topLevel, tables, List.of());
  }

  private void walkBody(
      Element root,
      List<DocumentSection> topLevel,
      List<ExtractedTable> tables,
      DocumentSection[] stack,
      StringBuilder currentContent,
      int[] tableIndex,
      int[] charOffset) {

    NodeList children = root.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      Element el = (Element) child;
      String tag = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
      tag = tag.toLowerCase();

      if (tag.matches("h[1-6]")) {
        int level = Integer.parseInt(tag.substring(1));
        String headingText = el.getTextContent().trim();

        // Finalise current content
        if (currentContent.length() > 0) {
          appendContent(stack, currentContent.toString());
          currentContent.setLength(0);
        }

        List<String> breadcrumb = buildBreadcrumb(stack, level, headingText);
        DocumentSection section =
            new DocumentSection(
                headingText,
                level,
                breadcrumb,
                "",
                new ArrayList<>(),
                charOffset[0],
                charOffset[0]);
        addToParent(stack, topLevel, level, section);
        stack[level] = section;
        for (int j = level + 1; j <= 6; j++) {
          stack[j] = null;
        }
        charOffset[0] += headingText.length() + 1;

      } else if ("table".equals(tag)) {
        String markdown = tableToMarkdown(el);
        tables.add(new ExtractedTable(tableIndex[0]++, markdown, "", charOffset[0]));
        currentContent.append(markdown).append("\n\n");
        charOffset[0] += markdown.length() + 2;

      } else if ("p".equals(tag) || "div".equals(tag) || "li".equals(tag)) {
        String text = el.getTextContent().trim();
        if (!text.isBlank()) {
          currentContent.append(text).append("\n\n");
          charOffset[0] += text.length() + 2;
        }
      } else {
        // Recurse into other block elements (body, section, article, …)
        walkBody(el, topLevel, tables, stack, currentContent, tableIndex, charOffset);
      }
    }
  }

  private String tableToMarkdown(Element tableEl) {
    StringBuilder sb = new StringBuilder();
    NodeList rows = tableEl.getElementsByTagNameNS("*", "tr");
    boolean headerDone = false;

    for (int r = 0; r < rows.getLength(); r++) {
      Element row = (Element) rows.item(r);
      NodeList cells = row.getChildNodes();
      StringBuilder rowSb = new StringBuilder("|");
      boolean hasCells = false;

      for (int c = 0; c < cells.getLength(); c++) {
        Node cell = cells.item(c);
        if (cell.getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }
        String cellTag = ((Element) cell).getLocalName();
        if (cellTag == null) {
          cellTag = ((Element) cell).getTagName();
        }
        if ("td".equalsIgnoreCase(cellTag) || "th".equalsIgnoreCase(cellTag)) {
          rowSb.append(" ").append(cell.getTextContent().trim()).append(" |");
          hasCells = true;
        }
      }

      if (!hasCells) {
        continue;
      }
      sb.append(rowSb).append("\n");
      if (!headerDone) {
        // Add separator row
        int pipeCount = (int) rowSb.chars().filter(ch -> ch == '|').count() - 1;
        sb.append("|");
        for (int p = 0; p < pipeCount; p++) {
          sb.append("---|");
        }
        sb.append("\n");
        headerDone = true;
      }
    }
    return sb.toString();
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
    for (int i = level - 1; i >= 1; i--) {
      if (stack[i] != null) {
        stack[i].children().add(section);
        return;
      }
    }
    topLevel.add(section);
  }

  private void appendContent(DocumentSection[] stack, String content) {
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
        replaceInParent(stack, i, updated);
        stack[i] = updated;
        return;
      }
    }
  }

  private void replaceInParent(DocumentSection[] stack, int level, DocumentSection updated) {
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
  }

  private void collectText(Element el, StringBuilder sb) {
    NodeList children = el.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.TEXT_NODE) {
        String text = child.getTextContent();
        if (text != null && !text.isBlank()) {
          sb.append(text.trim()).append(" ");
        }
      } else if (child.getNodeType() == Node.ELEMENT_NODE) {
        collectText((Element) child, sb);
      }
    }
  }
}
