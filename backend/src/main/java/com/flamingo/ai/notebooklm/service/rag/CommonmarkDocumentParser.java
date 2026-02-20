package com.flamingo.ai.notebooklm.service.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Service;

/**
 * {@link DocumentParser} implementation for Markdown files ({@code .md}, {@code text/markdown}).
 *
 * <p>Uses the {@code commonmark-java} library to parse the Markdown AST and extract:
 *
 * <ul>
 *   <li>{@link Heading} nodes → {@link DocumentSection} hierarchy (levels 1–6)
 *   <li>{@link Paragraph}, {@link BulletList}, {@link OrderedList} → section content (preserved as
 *       Markdown for downstream rendering)
 *   <li>{@link FencedCodeBlock}, {@link IndentedCodeBlock} → included in section content
 * </ul>
 *
 * <p>External image references ({@code ![alt](url)}) are intentionally ignored because the URL
 * points to a remote resource not managed by the system.
 */
@Service
@Slf4j
public class CommonmarkDocumentParser implements DocumentParser {

  private static final Set<String> SUPPORTED_TYPES =
      Set.of("text/markdown", "text/x-markdown", "text/plain");

  private static final Parser PARSER = Parser.builder().build();
  private static final TextContentRenderer TEXT_RENDERER = TextContentRenderer.builder().build();

  @Override
  public ParsedDocument parse(InputStream inputStream, String mimeType) {
    try {
      String markdownText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      return parseMarkdown(markdownText);
    } catch (IOException e) {
      log.error("CommonmarkDocumentParser failed: {}", e.getMessage());
      throw new com.flamingo.ai.notebooklm.exception.DocumentProcessingException(
          null, "Failed to parse Markdown: " + e.getMessage());
    }
  }

  @Override
  public boolean supports(String mimeType) {
    if (mimeType == null) {
      return false;
    }
    return SUPPORTED_TYPES.contains(mimeType.toLowerCase());
  }

  // ---- private helpers ----

  private ParsedDocument parseMarkdown(String markdownText) {
    org.commonmark.node.Node document = PARSER.parse(markdownText);
    String fullText = TEXT_RENDERER.render(document);

    MarkdownStructureVisitor visitor = new MarkdownStructureVisitor();
    document.accept(visitor);
    List<DocumentSection> sections = visitor.getSections();

    if (sections.isEmpty() && !fullText.isBlank()) {
      sections =
          List.of(
              new DocumentSection(
                  "Document", 1, List.of("Document"), fullText, List.of(), 0, fullText.length()));
    }

    return new ParsedDocument(fullText, sections, List.of(), List.of());
  }

  // ---- inner visitor ----

  private static final class MarkdownStructureVisitor extends AbstractVisitor {

    private final List<DocumentSection> topLevel = new ArrayList<>();
    private final DocumentSection[] stack = new DocumentSection[7];
    private final StringBuilder currentContent = new StringBuilder();
    private int charOffset = 0;

    @Override
    public void visit(Heading heading) {
      // Finalise any accumulated content
      if (currentContent.length() > 0) {
        appendContent(stack, currentContent.toString());
        currentContent.setLength(0);
      }

      String title = extractText(heading);
      int level = heading.getLevel();
      List<String> breadcrumb = buildBreadcrumb(stack, level, title);
      DocumentSection section =
          new DocumentSection(
              title, level, breadcrumb, "", new ArrayList<>(), charOffset, charOffset);
      addToParent(stack, topLevel, level, section);
      stack[level] = section;
      for (int i = level + 1; i <= 6; i++) {
        stack[i] = null;
      }
      charOffset += title.length() + 1;
    }

    @Override
    public void visit(Paragraph paragraph) {
      String text = extractText(paragraph);
      if (!text.isBlank()) {
        currentContent.append(text).append("\n\n");
        charOffset += text.length() + 2;
      }
    }

    @Override
    public void visit(BulletList list) {
      StringBuilder listText = new StringBuilder();
      collectListItems(list, listText, "-");
      currentContent.append(listText).append("\n");
      charOffset += listText.length() + 1;
    }

    @Override
    public void visit(OrderedList list) {
      StringBuilder listText = new StringBuilder();
      collectListItems(list, listText, "1.");
      currentContent.append(listText).append("\n");
      charOffset += listText.length() + 1;
    }

    @Override
    public void visit(FencedCodeBlock codeBlock) {
      String lang = codeBlock.getInfo() != null ? codeBlock.getInfo() : "";
      String code = "```" + lang + "\n" + codeBlock.getLiteral() + "```\n\n";
      currentContent.append(code);
      charOffset += code.length();
    }

    @Override
    public void visit(IndentedCodeBlock codeBlock) {
      String code = "```\n" + codeBlock.getLiteral() + "```\n\n";
      currentContent.append(code);
      charOffset += code.length();
    }

    List<DocumentSection> getSections() {
      if (currentContent.length() > 0) {
        appendContent(stack, currentContent.toString());
      }
      return topLevel;
    }

    private String extractText(Node node) {
      StringBuilder sb = new StringBuilder();
      collectNodeText(node, sb);
      return sb.toString().trim();
    }

    private void collectNodeText(Node node, StringBuilder sb) {
      if (node instanceof Text textNode) {
        sb.append(textNode.getLiteral());
      } else if (node instanceof SoftLineBreak) {
        sb.append(" ");
      } else {
        Node child = node.getFirstChild();
        while (child != null) {
          collectNodeText(child, sb);
          child = child.getNext();
        }
      }
    }

    private void collectListItems(Node list, StringBuilder sb, String prefix) {
      Node item = list.getFirstChild();
      int num = 1;
      while (item != null) {
        String text = extractText(item);
        if (!text.isBlank()) {
          if ("1.".equals(prefix)) {
            sb.append(num++).append(". ").append(text).append("\n");
          } else {
            sb.append("- ").append(text).append("\n");
          }
        }
        item = item.getNext();
      }
    }

    private List<String> buildBreadcrumb(DocumentSection[] sectionStack, int level, String title) {
      List<String> crumb = new ArrayList<>();
      for (int i = 1; i < level; i++) {
        if (sectionStack[i] != null) {
          crumb.add(sectionStack[i].title());
        }
      }
      crumb.add(title);
      return crumb;
    }

    private void addToParent(
        DocumentSection[] sectionStack,
        List<DocumentSection> roots,
        int level,
        DocumentSection section) {
      for (int i = level - 1; i >= 1; i--) {
        if (sectionStack[i] != null) {
          sectionStack[i].children().add(section);
          return;
        }
      }
      roots.add(section);
    }

    private void appendContent(DocumentSection[] sectionStack, String content) {
      for (int i = 6; i >= 1; i--) {
        if (sectionStack[i] != null) {
          String merged = sectionStack[i].content() + content;
          DocumentSection updated =
              new DocumentSection(
                  sectionStack[i].title(),
                  sectionStack[i].level(),
                  sectionStack[i].breadcrumb(),
                  merged,
                  sectionStack[i].children(),
                  sectionStack[i].startOffset(),
                  sectionStack[i].endOffset());
          replaceInParent(sectionStack, i, updated);
          sectionStack[i] = updated;
          return;
        }
      }
    }

    private void replaceInParent(
        DocumentSection[] sectionStack, int level, DocumentSection updated) {
      for (int i = level - 1; i >= 1; i--) {
        if (sectionStack[i] != null) {
          List<DocumentSection> children = sectionStack[i].children();
          int idx = children.size() - 1;
          if (idx >= 0) {
            children.set(idx, updated);
          }
          return;
        }
      }
    }
  }
}
