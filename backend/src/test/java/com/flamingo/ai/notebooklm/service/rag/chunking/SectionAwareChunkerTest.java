package com.flamingo.ai.notebooklm.service.rag.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.service.rag.image.ImageGroupingStrategy;
import com.flamingo.ai.notebooklm.service.rag.model.DocumentSection;
import com.flamingo.ai.notebooklm.service.rag.model.ParsedDocument;
import com.flamingo.ai.notebooklm.service.rag.model.RawDocumentChunk;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SectionAwareChunker Tests")
class SectionAwareChunkerTest {

  @Mock private ImageGroupingStrategy imageGroupingStrategy;

  private SectionAwareChunker chunker;
  private RagConfig ragConfig;

  @BeforeEach
  void setUp() {
    chunker = new SectionAwareChunker(imageGroupingStrategy);
    ragConfig = new RagConfig();
    ragConfig.getChunking().setSize(400);
    ragConfig.getChunking().setOverlap(50);
  }

  @Test
  @DisplayName("should fall back to fullText when sections exist but all have empty content")
  void shouldFallBackToFullText_whenSectionsExistButAllEmpty() {
    DocumentSection emptySection =
        new DocumentSection("Title", 1, List.of("Title"), "", new ArrayList<>(), 0, 0);
    ParsedDocument doc =
        new ParsedDocument(
            "This is the full text of the document with enough content to chunk.",
            List.of(emptySection),
            List.of(),
            List.of());

    List<RawDocumentChunk> chunks = chunker.chunk(doc, ragConfig);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).content()).contains("full text of the document");
  }

  @Test
  @DisplayName("should produce chunks when sections have content")
  void shouldProduceChunks_whenSectionsHaveContent() {
    DocumentSection section =
        new DocumentSection(
            "Chapter 1",
            1,
            List.of("Chapter 1"),
            "This section has meaningful content about the topic.",
            new ArrayList<>(),
            0,
            50);
    ParsedDocument doc =
        new ParsedDocument("Full text here.", List.of(section), List.of(), List.of());

    List<RawDocumentChunk> chunks = chunker.chunk(doc, ragConfig);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).content()).contains("meaningful content");
  }

  @Test
  @DisplayName("should fall back to fullText when no sections exist")
  void shouldFallBackToFullText_whenNoSections() {
    ParsedDocument doc =
        new ParsedDocument(
            "A document without any headings or structure, just plain text content.",
            List.of(),
            List.of(),
            List.of());

    List<RawDocumentChunk> chunks = chunker.chunk(doc, ragConfig);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).sectionBreadcrumb()).isEmpty();
  }

  @Test
  @DisplayName("should preserve breadcrumb in chunks")
  void shouldPreserveBreadcrumb_inChunks() {
    List<String> breadcrumb = List.of("Report", "Chapter 1", "Section 1.1");
    DocumentSection section =
        new DocumentSection(
            "Section 1.1",
            3,
            breadcrumb,
            "Detailed content of this subsection.",
            new ArrayList<>(),
            0,
            36);
    DocumentSection chapter =
        new DocumentSection(
            "Chapter 1", 2, List.of("Report", "Chapter 1"), "", List.of(section), 0, 0);
    DocumentSection report =
        new DocumentSection("Report", 1, List.of("Report"), "", List.of(chapter), 0, 0);
    ParsedDocument doc = new ParsedDocument("Full text.", List.of(report), List.of(), List.of());

    List<RawDocumentChunk> chunks = chunker.chunk(doc, ragConfig);

    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).sectionBreadcrumb())
        .containsExactly("Report", "Chapter 1", "Section 1.1");
  }

  @Test
  @DisplayName("should produce only blank chunks when document has empty text")
  void shouldProduceOnlyBlankChunks_whenDocumentHasEmptyText() {
    // slidingWindow("") produces [""] — upstream DocumentProcessingService guards against this
    ParsedDocument doc = new ParsedDocument("", List.of(), List.of(), List.of());

    List<RawDocumentChunk> chunks = chunker.chunk(doc, ragConfig);

    for (RawDocumentChunk chunk : chunks) {
      assertThat(chunk.content().trim()).isEmpty();
    }
  }
}
