package com.flamingo.ai.notebooklm.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.flamingo.ai.notebooklm.config.RagConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("DocumentMetadataExtractor Tests")
class DocumentMetadataExtractorTest {

  private DocumentMetadataExtractor extractor;
  private RagConfig ragConfig;

  @BeforeEach
  void setUp() {
    ragConfig = new RagConfig();
    RagConfig.Metadata metadataConfig = new RagConfig.Metadata();
    metadataConfig.setMaxKeywords(10);
    ReflectionTestUtils.setField(ragConfig, "metadata", metadataConfig);

    extractor = new DocumentMetadataExtractor(ragConfig);
  }

  @Test
  @DisplayName("Should extract title from Markdown header")
  void shouldExtractTitleFromMarkdownHeader() {
    String content = "# Machine Learning Guide\n\nThis is the introduction...";

    String title = extractor.extractTitle(content, "document.pdf");

    assertThat(title).isEqualTo("Machine Learning Guide");
  }

  @Test
  @DisplayName("Should extract title from underline-style header")
  void shouldExtractTitleFromUnderlineHeader() {
    String content = "Deep Learning Basics\n===================\n\nContent here...";

    String title = extractor.extractTitle(content, "document.pdf");

    assertThat(title).isEqualTo("Deep Learning Basics");
  }

  @Test
  @DisplayName("Should extract title from numbered header")
  void shouldExtractTitleFromNumberedHeader() {
    String content = "1. Introduction to AI\n\nThis section covers...";

    String title = extractor.extractTitle(content, "document.pdf");

    assertThat(title).isEqualTo("Introduction to AI");
  }

  @Test
  @DisplayName("Should extract title from first short line")
  void shouldExtractTitleFromFirstShortLine() {
    String content =
        "Neural Networks Overview\n\nThis document explains neural networks in detail...";

    String title = extractor.extractTitle(content, "document.pdf");

    assertThat(title).isEqualTo("Neural Networks Overview");
  }

  @Test
  @DisplayName("Should fall back to filename when no title found")
  void shouldFallBackToFilenameWhenNoTitleFound() {
    String content = "This is a very long sentence with multiple words. It continues on...";

    String title = extractor.extractTitle(content, "machine_learning-guide.pdf");

    assertThat(title).isEqualTo("Machine learning guide");
  }

  @Test
  @DisplayName("Should clean filename by removing extension and formatting")
  void shouldCleanFilenameByRemovingExtensionAndFormatting() {
    String content = null;

    String title = extractor.extractTitle(content, "deep_learning-basics.pdf");

    assertThat(title).isEqualTo("Deep learning basics");
  }

  @Test
  @DisplayName("Should handle empty content gracefully")
  void shouldHandleEmptyContentGracefully() {
    String content = "";

    String title = extractor.extractTitle(content, "document.pdf");

    assertThat(title).isEqualTo("Document");
  }

  @Test
  @DisplayName("Should handle null filename gracefully")
  void shouldHandleNullFilenameGracefully() {
    String content = null;

    String title = extractor.extractTitle(content, null);

    assertThat(title).isEqualTo("Unknown Document");
  }

  @Test
  @DisplayName("Should extract multiple Markdown sections")
  void shouldExtractMultipleMarkdownSections() {
    String content =
        """
            # Introduction
            Content here...

            ## Background
            More content...

            ### Technical Details
            Even more content...
            """;

    List<String> sections = extractor.extractSections(content);

    assertThat(sections).containsExactly("Introduction", "Background", "Technical Details");
  }

  @Test
  @DisplayName("Should extract underline-style sections")
  void shouldExtractUnderlineStyleSections() {
    String content =
        """
            Section One
            ===========

            Content...

            Section Two
            -----------

            More content...
            """;

    List<String> sections = extractor.extractSections(content);

    assertThat(sections).contains("Section One", "Section Two");
  }

  @Test
  @DisplayName("Should extract numbered sections")
  void shouldExtractNumberedSections() {
    String content =
        """
            1. First Section
            Content...

            2. Second Section
            More content...
            """;

    List<String> sections = extractor.extractSections(content);

    assertThat(sections).contains("First Section", "Second Section");
  }

  @Test
  @DisplayName("Should extract ALL-CAPS sections")
  void shouldExtractAllCapsSections() {
    String content =
        """
            INTRODUCTION

            Content here...

            METHODOLOGY

            More content...
            """;

    List<String> sections = extractor.extractSections(content);

    assertThat(sections).contains("INTRODUCTION", "METHODOLOGY");
  }

  @Test
  @DisplayName("Should deduplicate section headers")
  void shouldDeduplicateSectionHeaders() {
    String content =
        """
            # Introduction
            Content...

            ## Introduction
            More content with same name...
            """;

    List<String> sections = extractor.extractSections(content);

    assertThat(sections).hasSize(1).contains("Introduction");
  }

  @Test
  @DisplayName("Should filter out very short ALL-CAPS headers")
  void shouldFilterOutVeryShortAllCapsHeaders() {
    String content =
        """
            ABC

            Content...

            INTRODUCTION

            More content...
            """;

    List<String> sections = extractor.extractSections(content);

    assertThat(sections).contains("INTRODUCTION").doesNotContain("ABC");
  }

  @Test
  @DisplayName("Should return empty list for null content")
  void shouldReturnEmptyListForNullContent() {
    List<String> sections = extractor.extractSections(null);

    assertThat(sections).isEmpty();
  }

  @Test
  @DisplayName("Should find correct section for chunk position")
  void shouldFindCorrectSectionForChunkPosition() {
    String content =
        """
            # Introduction
            This is the introduction section.

            ## Background
            This is the background section.

            ### Details
            This is the details section.
            """;

    String section = extractor.findChunkSection(content, content.indexOf("background"));

    assertThat(section).isEqualTo("Background");
  }

  @Test
  @DisplayName("Should find last section before chunk position")
  void shouldFindLastSectionBeforeChunkPosition() {
    String content =
        """
            # Introduction
            Content...

            ## Section One
            More content...

            ## Section Two
            Even more content...
            """;

    int chunkStart = content.indexOf("Even more");
    String section = extractor.findChunkSection(content, chunkStart);

    assertThat(section).isEqualTo("Section Two");
  }

  @Test
  @DisplayName("Should return null when chunk position is before any section")
  void shouldReturnNullWhenChunkPositionIsBeforeAnySection() {
    String content = "Some preamble text\n\n# First Section\nContent...";

    String section = extractor.findChunkSection(content, 5);

    assertThat(section).isNull();
  }

  @Test
  @DisplayName("Should handle null content for chunk section")
  void shouldHandleNullContentForChunkSection() {
    String section = extractor.findChunkSection(null, 100);

    assertThat(section).isNull();
  }

  @Test
  @DisplayName("Should handle negative chunk position")
  void shouldHandleNegativeChunkPosition() {
    String content = "# Section\nContent...";

    String section = extractor.findChunkSection(content, -1);

    assertThat(section).isNull();
  }

  @Test
  @DisplayName("Should extract keywords with TF-IDF scoring")
  void shouldExtractKeywordsWithTfIdfScoring() {
    String content =
        "Machine learning is a subset of artificial intelligence. "
            + "Machine learning algorithms learn from data. "
            + "Deep learning is a subset of machine learning.";

    List<String> keywords = extractor.extractKeywords(content, 5);

    assertThat(keywords).contains("machine", "learning");
  }

  @Test
  @DisplayName("Should filter out stopwords from keywords")
  void shouldFilterOutStopwordsFromKeywords() {
    String content = "This is a test of the keyword extraction system.";

    List<String> keywords = extractor.extractKeywords(content, 10);

    assertThat(keywords).doesNotContain("this", "is", "a", "the", "of");
  }

  @Test
  @DisplayName("Should handle CJK characters in keyword extraction")
  void shouldHandleCjkCharactersInKeywordExtraction() {
    String content = "机器学习 是 人工智能 的一个分支。机器学习 算法从数据中学习。";

    List<String> keywords = extractor.extractKeywords(content, 5);

    assertThat(keywords).contains("机器学习", "人工智能");
  }

  @Test
  @DisplayName("Should apply length bonus for longer keywords")
  void shouldApplyLengthBonusForLongerKeywords() {
    String content =
        "AI AI AI AI AI AI "
            + "artificialintelligence artificialintelligence artificialintelligence";

    List<String> keywords = extractor.extractKeywords(content, 5);

    // Longer term should rank higher despite lower frequency
    assertThat(keywords.get(0)).isEqualTo("artificialintelligence");
  }

  @Test
  @DisplayName("Should penalize very frequent terms")
  void shouldPenalizeVeryFrequentTerms() {
    String content = "test " + "word ".repeat(100); // "word" appears > 10% of tokens

    List<String> keywords = extractor.extractKeywords(content, 5);

    // "word" should be penalized due to high frequency
    assertThat(keywords).contains("test");
  }

  @Test
  @DisplayName("Should filter out very short keywords")
  void shouldFilterOutVeryShortKeywords() {
    String content = "a ab abc machine learning ai ml";

    List<String> keywords = extractor.extractKeywords(content, 10);

    assertThat(keywords).contains("machine", "learning").doesNotContain("a", "ab");
  }

  @Test
  @DisplayName("Should return empty list for null content in keywords")
  void shouldReturnEmptyListForNullContentInKeywords() {
    List<String> keywords = extractor.extractKeywords(null, 5);

    assertThat(keywords).isEmpty();
  }

  @Test
  @DisplayName("Should return empty list for empty content in keywords")
  void shouldReturnEmptyListForEmptyContentInKeywords() {
    List<String> keywords = extractor.extractKeywords("", 5);

    assertThat(keywords).isEmpty();
  }

  @Test
  @DisplayName("Should return empty list when all tokens are stopwords")
  void shouldReturnEmptyListWhenAllTokensAreStopwords() {
    String content = "the a an and or but is are was were";

    List<String> keywords = extractor.extractKeywords(content, 5);

    assertThat(keywords).isEmpty();
  }

  @Test
  @DisplayName("Should use config default for keyword count")
  void shouldUseConfigDefaultForKeywordCount() {
    String content =
        "machine learning artificial intelligence deep learning "
            + "neural networks natural language processing computer vision "
            + "reinforcement learning supervised learning unsupervised learning";

    List<String> keywords = extractor.extractKeywords(content);

    assertThat(keywords).hasSizeLessThanOrEqualTo(10); // maxKeywords from config
  }

  @Test
  @DisplayName("Should build enriched content with all metadata")
  void shouldBuildEnrichedContentWithAllMetadata() {
    String enriched =
        extractor.buildEnrichedContent(
            "Content here", "Machine Learning", "Introduction", List.of("ai", "ml", "algorithms"));

    assertThat(enriched)
        .contains("[Document: Machine Learning]")
        .contains("[Section: Introduction]")
        .contains("[Keywords: ai, ml, algorithms]")
        .contains("Content here");
  }

  @Test
  @DisplayName("Should build enriched content without section if null")
  void shouldBuildEnrichedContentWithoutSectionIfNull() {
    String enriched =
        extractor.buildEnrichedContent(
            "Content here", "Machine Learning", null, List.of("ai", "ml"));

    assertThat(enriched)
        .contains("[Document: Machine Learning]")
        .doesNotContain("[Section:")
        .contains("[Keywords: ai, ml]")
        .contains("Content here");
  }

  @Test
  @DisplayName("Should build enriched content without keywords if empty")
  void shouldBuildEnrichedContentWithoutKeywordsIfEmpty() {
    String enriched =
        extractor.buildEnrichedContent(
            "Content here", "Machine Learning", "Introduction", List.of());

    assertThat(enriched)
        .contains("[Document: Machine Learning]")
        .contains("[Section: Introduction]")
        .doesNotContain("[Keywords:")
        .contains("Content here");
  }

  @Test
  @DisplayName("Should build content-only when all metadata is null")
  void shouldBuildContentOnlyWhenAllMetadataIsNull() {
    String enriched = extractor.buildEnrichedContent("Content here", null, null, null);

    assertThat(enriched).isEqualTo("Content here");
  }

  @Test
  @DisplayName("Should handle empty strings in enriched content")
  void shouldHandleEmptyStringsInEnrichedContent() {
    String enriched = extractor.buildEnrichedContent("Content here", "", "", List.of());

    assertThat(enriched).isEqualTo("Content here");
  }
}
