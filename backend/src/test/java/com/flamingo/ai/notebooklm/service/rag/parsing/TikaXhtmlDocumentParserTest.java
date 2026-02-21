package com.flamingo.ai.notebooklm.service.rag.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import com.flamingo.ai.notebooklm.service.rag.model.DocumentSection;
import com.flamingo.ai.notebooklm.service.rag.model.ParsedDocument;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TikaXhtmlDocumentParser Tests")
class TikaXhtmlDocumentParserTest {

  private TikaXhtmlDocumentParser parser;
  private Method parseXhtmlMethod;

  @BeforeEach
  void setUp() throws Exception {
    parser = new TikaXhtmlDocumentParser();
    parseXhtmlMethod = TikaXhtmlDocumentParser.class.getDeclaredMethod("parseXhtml", byte[].class);
    parseXhtmlMethod.setAccessible(true);
  }

  private ParsedDocument parseXhtml(String xhtml) throws Exception {
    String fullXhtml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>"
            + xhtml
            + "</body></html>";
    return (ParsedDocument)
        parseXhtmlMethod.invoke(parser, fullXhtml.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("should populate top-level section content when tables follow H1")
  void shouldPopulateTopLevelSectionContent_whenTablesFollowH1() throws Exception {
    String xhtml =
        "<h1>Report Title</h1>"
            + "<table><tr><th>Col A</th><th>Col B</th></tr>"
            + "<tr><td>1</td><td>2</td></tr></table>"
            + "<p>Some paragraph text after table.</p>";

    ParsedDocument result = parseXhtml(xhtml);

    assertThat(result.sections()).hasSize(1);
    DocumentSection section = result.sections().get(0);
    assertThat(section.title()).isEqualTo("Report Title");
    assertThat(section.level()).isEqualTo(1);
    assertThat(section.content()).contains("|");
    assertThat(section.content()).contains("Some paragraph text after table.");
  }

  @Test
  @DisplayName("should preserve content before first heading")
  void shouldPreserveContentBeforeFirstHeading() throws Exception {
    String xhtml =
        "<p>Preamble text before any heading.</p>"
            + "<h1>First Heading</h1>"
            + "<p>Content under heading.</p>";

    ParsedDocument result = parseXhtml(xhtml);

    assertThat(result.sections()).isNotEmpty();
    // The preamble should be preserved — either in its own synthetic section or prepended
    String allContent = collectAllContent(result.sections());
    assertThat(allContent).contains("Preamble text before any heading.");
    assertThat(allContent).contains("Content under heading.");
  }

  @Test
  @DisplayName("should handle document with only tables and no headings")
  void shouldHandleDocumentWithOnlyTables_noHeadings() throws Exception {
    String xhtml =
        "<table><tr><th>Name</th><th>Value</th></tr>"
            + "<tr><td>A</td><td>100</td></tr></table>"
            + "<table><tr><th>X</th><th>Y</th></tr>"
            + "<tr><td>1</td><td>2</td></tr></table>";

    ParsedDocument result = parseXhtml(xhtml);

    assertThat(result.sections()).isNotEmpty();
    DocumentSection section = result.sections().get(0);
    assertThat(section.title()).isEqualTo("Document");
    assertThat(section.content()).contains("|");
  }

  @Test
  @DisplayName("should build correct hierarchy when nested headings")
  void shouldBuildCorrectHierarchy_whenNestedHeadings() throws Exception {
    String xhtml =
        "<h1>Chapter</h1>"
            + "<p>Chapter intro.</p>"
            + "<h2>Section A</h2>"
            + "<p>Section A content.</p>"
            + "<h2>Section B</h2>"
            + "<p>Section B content.</p>";

    ParsedDocument result = parseXhtml(xhtml);

    assertThat(result.sections()).hasSize(1);
    DocumentSection chapter = result.sections().get(0);
    assertThat(chapter.title()).isEqualTo("Chapter");
    assertThat(chapter.content()).contains("Chapter intro.");
    assertThat(chapter.children()).hasSize(2);
    assertThat(chapter.children().get(0).title()).isEqualTo("Section A");
    assertThat(chapter.children().get(0).content()).contains("Section A content.");
    assertThat(chapter.children().get(1).title()).isEqualTo("Section B");
    assertThat(chapter.children().get(1).content()).contains("Section B content.");
  }

  @Test
  @DisplayName("should handle trailing content after last heading")
  void shouldHandleTrailingContent_afterLastHeading() throws Exception {
    String xhtml = "<h1>Title</h1>" + "<p>First paragraph.</p>" + "<p>Trailing paragraph.</p>";

    ParsedDocument result = parseXhtml(xhtml);

    assertThat(result.sections()).hasSize(1);
    DocumentSection section = result.sections().get(0);
    assertThat(section.content()).contains("First paragraph.");
    assertThat(section.content()).contains("Trailing paragraph.");
  }

  @Test
  @DisplayName("should handle multiple top-level H1 sections with content")
  void shouldHandleMultipleTopLevelH1Sections_withContent() throws Exception {
    String xhtml =
        "<h1>Section One</h1>"
            + "<p>Content one.</p>"
            + "<h1>Section Two</h1>"
            + "<p>Content two.</p>";

    ParsedDocument result = parseXhtml(xhtml);

    assertThat(result.sections()).hasSize(2);
    assertThat(result.sections().get(0).title()).isEqualTo("Section One");
    assertThat(result.sections().get(0).content()).contains("Content one.");
    assertThat(result.sections().get(1).title()).isEqualTo("Section Two");
    assertThat(result.sections().get(1).content()).contains("Content two.");
  }

  @Test
  @DisplayName("should produce non-empty fullText for any document with content")
  void shouldProduceNonEmptyFullText_forDocumentWithContent() throws Exception {
    String xhtml = "<p>Some text content.</p>";

    ParsedDocument result = parseXhtml(xhtml);

    assertThat(result.fullText()).contains("Some text content.");
  }

  private String collectAllContent(List<DocumentSection> sections) {
    StringBuilder sb = new StringBuilder();
    for (DocumentSection section : sections) {
      sb.append(section.content());
      sb.append(collectAllContent(section.children()));
    }
    return sb.toString();
  }
}
