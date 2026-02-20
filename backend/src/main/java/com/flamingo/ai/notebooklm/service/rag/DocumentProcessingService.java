package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.domain.enums.DocumentStatus;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunkIndexService;
import com.flamingo.ai.notebooklm.exception.DocumentProcessingException;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates document processing: parse, chunk, embed, and index.
 *
 * <p>Delegates parsing and chunking to {@link DocumentChunkingStrategyRouter} so this service has
 * no knowledge of any specific parser or chunker technology. Switching from Java-native parsing
 * (PDFBox + Tika) to Docling requires zero changes here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

  private final DocumentRepository documentRepository;
  private final DocumentChunkIndexService documentChunkIndexService;
  private final EmbeddingService embeddingService;
  private final DocumentMetadataExtractor metadataExtractor;
  private final DocumentChunkingStrategyRouter strategyRouter;
  private final ImageStorageService imageStorageService;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  private static final int MAX_RETRIES = 3;
  private static final long RETRY_DELAY_MS = 100;

  /**
   * Processes a document asynchronously: parse, chunk, store images, embed, and index.
   *
   * @param documentId the document to process
   * @param inputStream the document content stream
   */
  @Timed(value = "document.process", description = "Time to process document")
  @Async("documentProcessingExecutor")
  public void processDocumentAsync(UUID documentId, InputStream inputStream) {
    try {
      Document document = getDocumentWithRetry(documentId);
      updateDocumentStatusWithRetry(documentId, DocumentStatus.PROCESSING, null, null);

      // Read inputStream into byte array so we can reuse it for composite rendering
      byte[] documentBytes = inputStream.readAllBytes();

      // --- 1. Chunk via strategy router ---
      DocumentContext context =
          new DocumentContext(
              documentId,
              document.getSession().getId(),
              document.getFileName(),
              document.getMimeType());

      DocumentChunkingStrategy strategy = strategyRouter.route(document.getMimeType());
      ChunkingResult result =
          strategy.chunkDocument(new java.io.ByteArrayInputStream(documentBytes), context);

      List<RawDocumentChunk> rawChunks = result.chunks();
      String fullText = result.fullText();

      if (rawChunks.isEmpty() && (fullText == null || fullText.isBlank())) {
        throw new DocumentProcessingException(documentId, "No content extracted from document");
      }

      log.info("Document {} split into {} chunks", documentId, rawChunks.size());

      // --- 2. Store extracted images (with composite rendering for spatial groups) ---
      Map<Integer, String> imageIndexToId =
          imageStorageService.storeImages(
              result.extractedImages(), documentBytes, documentId, document.getSession().getId());

      // --- 3. Extract document-level metadata ---
      String documentTitle =
          metadataExtractor.extractTitle(fullText != null ? fullText : "", document.getFileName());
      List<String> documentKeywords =
          ragConfig.getMetadata().isExtractKeywords()
              ? metadataExtractor.extractKeywords(fullText != null ? fullText : "")
              : List.of();
      log.debug("Extracted metadata - title: {}, keywords: {}", documentTitle, documentKeywords);

      // --- 4. Build DocumentChunk objects ---
      List<DocumentChunk> documentChunks = new ArrayList<>();
      List<String> textsToEmbed = new ArrayList<>();

      for (RawDocumentChunk rawChunk : rawChunks) {
        String chunkContent = rawChunk.content();
        List<String> breadcrumb = rawChunk.sectionBreadcrumb();

        // Use breadcrumb join as the section title for backward-compatible metadata
        String sectionTitle = breadcrumb.isEmpty() ? null : String.join(" > ", breadcrumb);

        // Chunk-specific keywords
        List<String> chunkKeywords =
            ragConfig.getMetadata().isExtractKeywords()
                ? metadataExtractor.extractKeywords(chunkContent, 5)
                : List.of();

        // Build enriched content for embedding
        String enrichedContent =
            ragConfig.getMetadata().isEnrichChunks()
                ? metadataExtractor.buildEnrichedContent(
                    chunkContent, documentTitle, sectionTitle, chunkKeywords)
                : chunkContent;

        // Map image indices → UUIDs for this chunk
        List<String> chunkImageIds =
            rawChunk.associatedImageIndices().stream()
                .filter(imageIndexToId::containsKey)
                .map(imageIndexToId::get)
                .toList();

        // Embed image markers in enriched content (for LLM to reference)
        if (!chunkImageIds.isEmpty()) {
          enrichedContent =
              embedImageMarkers(enrichedContent, chunkImageIds, document.getFileName());
        }

        textsToEmbed.add(enrichedContent);

        documentChunks.add(
            DocumentChunk.builder()
                .id(documentId + "_" + rawChunk.chunkIndex())
                .documentId(documentId)
                .sessionId(document.getSession().getId())
                .fileName(document.getFileName())
                .chunkIndex(rawChunk.chunkIndex())
                .content(chunkContent)
                .documentTitle(documentTitle)
                .sectionTitle(sectionTitle)
                .sectionBreadcrumb(breadcrumb)
                .keywords(chunkKeywords)
                .enrichedContent(enrichedContent)
                .associatedImageIds(chunkImageIds)
                .tokenCount(estimateTokenCount(chunkContent))
                .build());
      }

      // --- 5. Generate embeddings ---
      log.debug("Generating title and content embeddings for {} chunks...", textsToEmbed.size());

      List<String> titleTexts = new ArrayList<>();
      for (DocumentChunk chunk : documentChunks) {
        String breadcrumbStr =
            chunk.getSectionBreadcrumb() != null
                ? String.join(" > ", chunk.getSectionBreadcrumb())
                : "";
        String titleText =
            (chunk.getDocumentTitle() != null ? chunk.getDocumentTitle() : "")
                + (breadcrumbStr.isEmpty() ? "" : " " + breadcrumbStr);
        titleTexts.add(titleText.isBlank() ? chunk.getFileName() : titleText.trim());
      }

      List<List<Float>> titleEmbeddings = embeddingService.embedTexts(titleTexts);
      List<List<Float>> contentEmbeddings = embeddingService.embedTexts(textsToEmbed);
      log.debug(
          "Generated {} title embeddings and {} content embeddings",
          titleEmbeddings.size(),
          contentEmbeddings.size());

      if (titleEmbeddings.isEmpty()
          || contentEmbeddings.isEmpty()
          || titleEmbeddings.size() != documentChunks.size()
          || contentEmbeddings.size() != documentChunks.size()) {
        throw new DocumentProcessingException(
            documentId,
            String.format(
                "Embedding generation failed: expected %d embeddings, got %d title and %d content",
                documentChunks.size(), titleEmbeddings.size(), contentEmbeddings.size()));
      }

      // --- 6. Attach embeddings and filter invalid chunks ---
      List<DocumentChunk> validChunks = new ArrayList<>();
      int skippedChunks = 0;

      for (int i = 0; i < documentChunks.size(); i++) {
        List<Float> titleEmbedding = titleEmbeddings.get(i);
        List<Float> contentEmbedding = contentEmbeddings.get(i);

        if (titleEmbedding == null
            || titleEmbedding.isEmpty()
            || contentEmbedding == null
            || contentEmbedding.isEmpty()) {
          log.warn("Skipping chunk {} due to empty embedding", i);
          skippedChunks++;
          continue;
        }

        documentChunks.get(i).setTitleEmbedding(titleEmbedding);
        documentChunks.get(i).setContentEmbedding(contentEmbedding);
        documentChunks.get(i).setEmbedding(contentEmbedding); // Backward compatibility
        validChunks.add(documentChunks.get(i));
      }

      if (skippedChunks > 0) {
        log.warn("Skipped {} chunks with failed embeddings", skippedChunks);
      }

      if (validChunks.isEmpty()
          || (skippedChunks > documentChunks.size() * 0.1 && documentChunks.size() > 10)) {
        throw new DocumentProcessingException(
            documentId,
            String.format(
                "Too many chunks failed embedding generation: %d/%d skipped",
                skippedChunks, documentChunks.size()));
      }

      // --- 7. Index to Elasticsearch ---
      log.info("========== INDEXING DOCUMENT {} ==========", documentId);
      log.info("Session ID: {}", document.getSession().getId());
      log.info("Document file name: {}", document.getFileName());
      log.info("Number of valid chunks to index: {}", validChunks.size());
      for (int i = 0; i < Math.min(3, validChunks.size()); i++) {
        DocumentChunk chunk = validChunks.get(i);
        log.info(
            "Sample chunk {}: id={}, sessionId={}, breadcrumb={}",
            i,
            chunk.getId(),
            chunk.getSessionId(),
            chunk.getSectionBreadcrumb());
        log.info("===== CHUNK {} FULL CONTENT START =====", i);
        log.info("{}", chunk.getContent());
        log.info("===== CHUNK {} FULL CONTENT END =====", i);
      }
      documentChunkIndexService.indexChunks(validChunks);
      log.info("Elasticsearch indexing complete for document {}", documentId);
      log.info("========== INDEXING COMPLETE ==========");

      updateDocumentStatusWithRetry(documentId, DocumentStatus.READY, validChunks.size(), null);
      meterRegistry.counter("document.processing.success").increment();
      log.info("Successfully processed document: {}", documentId);

    } catch (Exception e) {
      log.error("Failed to process document {}: {}", documentId, e.getMessage());
      meterRegistry.counter("document.processing.failure").increment();
      try {
        updateDocumentStatusWithRetry(documentId, DocumentStatus.FAILED, null, e.getMessage());
      } catch (Exception retryEx) {
        log.error("Failed to update document status after retries: {}", retryEx.getMessage());
      }
    }
  }

  /** Gets a document with retry logic for SQLite lock contention. */
  @Transactional(readOnly = true)
  public Document getDocumentWithRetry(UUID documentId) {
    return documentRepository
        .findById(documentId)
        .orElseThrow(() -> new DocumentProcessingException(documentId, "Document not found"));
  }

  /** Updates document status with retry logic for SQLite lock contention. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateDocumentStatusWithRetry(
      UUID documentId, DocumentStatus status, Integer chunkCount, String error) {
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        Document document =
            documentRepository
                .findById(documentId)
                .orElseThrow(
                    () -> new DocumentProcessingException(documentId, "Document not found"));

        document.setStatus(status);
        if (chunkCount != null) {
          document.setChunkCount(chunkCount);
        }
        if (error != null) {
          document.markFailed(error);
        }
        documentRepository.saveAndFlush(document);
        return;
      } catch (CannotAcquireLockException e) {
        if (attempt == MAX_RETRIES) {
          log.error("Failed to update document {} after {} retries", documentId, MAX_RETRIES);
          throw e;
        }
        log.warn(
            "SQLite lock contention on document {}, retry {}/{}", documentId, attempt, MAX_RETRIES);
        try {
          Thread.sleep(RETRY_DELAY_MS * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted during retry", ie);
        }
      }
    }
  }

  /**
   * Embeds image reference markers in chunk content so the LLM can reference them in responses.
   *
   * <p>Format: [IMAGE: filename - ID: uuid]
   *
   * <p>The LLM is instructed via system prompt to output these markers, which the frontend then
   * renders as actual images.
   */
  private String embedImageMarkers(String enrichedContent, List<String> imageIds, String fileName) {
    StringBuilder sb = new StringBuilder(enrichedContent);
    sb.append("\n\n");

    for (int i = 0; i < imageIds.size(); i++) {
      sb.append("[IMAGE: ")
          .append(fileName)
          .append(" - Figure ")
          .append(i + 1)
          .append(" - ID: ")
          .append(imageIds.get(i))
          .append("]\n");
    }

    return sb.toString();
  }

  private int estimateTokenCount(String text) {
    return text.length() / 4;
  }
}
