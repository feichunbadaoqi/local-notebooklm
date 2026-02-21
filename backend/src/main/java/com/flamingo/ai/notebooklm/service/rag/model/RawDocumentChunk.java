package com.flamingo.ai.notebooklm.service.rag.model;

import java.util.List;

/**
 * A single chunk produced by a {@link
 * com.flamingo.ai.notebooklm.service.rag.chunking.DocumentChunker}, before embedding or
 * Elasticsearch indexing.
 *
 * @param content the text content of the chunk (may include Markdown tables inline)
 * @param sectionBreadcrumb hierarchical path to the section containing this chunk, e.g. {@code
 *     ["Doc Title", "Chapter 2", "Section 2.1"]}; empty list for unstructured content
 * @param chunkIndex sequential position of this chunk within the document (0-based)
 * @param associatedImageIndices indices into {@link ParsedDocument#images()} whose approximate
 *     offset falls within the character range covered by this chunk
 * @param documentOffset approximate character offset of this chunk in the full document text, used
 *     for image-to-chunk association
 */
public record RawDocumentChunk(
    String content,
    List<String> sectionBreadcrumb,
    int chunkIndex,
    List<Integer> associatedImageIndices,
    int documentOffset) {}
