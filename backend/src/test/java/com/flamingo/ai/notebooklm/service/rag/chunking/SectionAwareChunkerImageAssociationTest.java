package com.flamingo.ai.notebooklm.service.rag.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.service.rag.image.ImageGroupingStrategy;
import com.flamingo.ai.notebooklm.service.rag.model.DocumentSection;
import com.flamingo.ai.notebooklm.service.rag.model.ExtractedImage;
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
@DisplayName("SectionAwareChunker Image Association Tests")
class SectionAwareChunkerImageAssociationTest {

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
  @DisplayName("should assign one representative index per spatial group")
  void shouldAssignOneRepresentativeIndexPerSpatialGroup() {
    // Given: a section with content and a spatial group of 5 images all on page 0
    String sectionContent = "This is a section about cloud architecture with diagrams.";
    DocumentSection section =
        new DocumentSection(
            "Architecture",
            1,
            List.of("Architecture"),
            sectionContent,
            new ArrayList<>(),
            0,
            sectionContent.length());

    // 5 images in the same spatial group (group 0), all on page 0
    List<ExtractedImage> images = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      images.add(
          new ExtractedImage(
              i, "image/png", new byte[] {1}, 100, 100, "", 10, 0, 50f + i * 20, 100f, 0));
    }

    ParsedDocument doc = new ParsedDocument(sectionContent, List.of(section), List.of(), images);

    // Mock grouping strategy to return images as-is (already grouped)
    when(imageGroupingStrategy.groupImages(anyList())).thenReturn(images);
    when(imageGroupingStrategy.getStrategyName()).thenReturn("test");

    // When
    List<RawDocumentChunk> chunks = chunker.chunk(doc, ragConfig);

    // Then: only ONE image index should be added (the representative), not all 5
    assertThat(chunks).isNotEmpty();
    RawDocumentChunk targetChunk = chunks.get(0);
    assertThat(targetChunk.associatedImageIndices()).hasSize(1);
    assertThat(targetChunk.associatedImageIndices()).containsExactly(0);
  }

  @Test
  @DisplayName("should associate images with correct chunks using document offsets")
  void shouldAssociateImagesWithCorrectChunks_usingDocumentOffsets() {
    // Given: two sections at different offsets, each with an image near it
    String content1 = "Introduction to cloud computing and its benefits for enterprise.";
    String content2 = "Advanced networking concepts for distributed systems.";

    DocumentSection section1 =
        new DocumentSection(
            "Introduction",
            1,
            List.of("Introduction"),
            content1,
            new ArrayList<>(),
            0,
            content1.length());

    DocumentSection section2 =
        new DocumentSection(
            "Networking",
            1,
            List.of("Networking"),
            content2,
            new ArrayList<>(),
            5000,
            5000 + content2.length());

    // Image near offset 0 (should go to chunk 0) — ungrouped
    ExtractedImage image0 =
        new ExtractedImage(0, "image/png", new byte[] {1}, 100, 100, "", 10, 0, 50f, 100f, -1);

    // Image near offset 5000 (should go to chunk 1) — ungrouped
    ExtractedImage image1 =
        new ExtractedImage(1, "image/png", new byte[] {1}, 100, 100, "", 5010, 5, 50f, 100f, -1);

    List<ExtractedImage> images = List.of(image0, image1);
    ParsedDocument doc =
        new ParsedDocument("full text", List.of(section1, section2), List.of(), images);

    when(imageGroupingStrategy.groupImages(anyList())).thenReturn(images);
    when(imageGroupingStrategy.getStrategyName()).thenReturn("test");

    // When
    List<RawDocumentChunk> chunks = chunker.chunk(doc, ragConfig);

    // Then: each image should be on its nearest chunk
    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).associatedImageIndices()).containsExactly(0);
    assertThat(chunks.get(1).associatedImageIndices()).containsExactly(1);
  }
}
