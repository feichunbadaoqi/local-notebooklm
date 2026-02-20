package com.flamingo.ai.notebooklm.service.rag;

import java.util.List;

/**
 * The composite result of a {@link DocumentChunkingStrategy#chunkDocument} call.
 *
 * <p>Bundles the produced chunks together with any images extracted from the document and the
 * document's full plain text (used for title extraction by the caller). Wrapping these values in a
 * single immutable record avoids mutable state on the strategy implementations and is safe for
 * concurrent use.
 *
 * @param chunks chunks ready for embedding and Elasticsearch indexing
 * @param extractedImages images extracted during parsing; may be empty
 * @param fullText complete plain text of the document, used by the caller for title/keyword
 *     extraction
 */
public record ChunkingResult(
    List<RawDocumentChunk> chunks, List<ExtractedImage> extractedImages, String fullText) {}
