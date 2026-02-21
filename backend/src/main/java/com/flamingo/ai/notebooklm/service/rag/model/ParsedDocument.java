package com.flamingo.ai.notebooklm.service.rag.model;

import java.util.List;

/**
 * The structured output produced by a {@link
 * com.flamingo.ai.notebooklm.service.rag.parsing.DocumentParser}.
 *
 * <p>Carries both the flat full-text (for BM25 indexing and title extraction) and the enriched
 * structural representation (sections, tables, images) used by {@link
 * com.flamingo.ai.notebooklm.service.rag.chunking.DocumentChunker} to produce semantically coherent
 * chunks.
 *
 * @param fullText concatenated plain text of the entire document
 * @param sections top-level section tree (may be empty for unstructured documents)
 * @param tables all tables extracted from the document
 * @param images all images embedded in the document
 */
public record ParsedDocument(
    String fullText,
    List<DocumentSection> sections,
    List<ExtractedTable> tables,
    List<ExtractedImage> images) {}
