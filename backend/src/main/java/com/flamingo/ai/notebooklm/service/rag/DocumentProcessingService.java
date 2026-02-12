package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.domain.enums.DocumentStatus;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import com.flamingo.ai.notebooklm.elasticsearch.ElasticsearchIndexService;
import com.flamingo.ai.notebooklm.exception.DocumentProcessingException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Service for processing documents: parsing with Tika, chunking, and indexing. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

  private final DocumentRepository documentRepository;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final EmbeddingService embeddingService;
  private final DocumentMetadataExtractor metadataExtractor;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  private final Tika tika = new Tika();

  private static final int MAX_RETRIES = 3;
  private static final long RETRY_DELAY_MS = 100;

  /**
   * Processes a document asynchronously: parse, chunk, embed, and index.
   *
   * @param documentId the document to process
   * @param inputStream the document content stream
   */
  @Async("documentProcessingExecutor")
  public void processDocumentAsync(UUID documentId, InputStream inputStream) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      Document document = getDocumentWithRetry(documentId);

      // Update status to processing
      updateDocumentStatusWithRetry(documentId, DocumentStatus.PROCESSING, null, null);

      // Parse document with Tika
      String content = parseDocument(inputStream);
      if (content == null || content.isBlank()) {
        throw new DocumentProcessingException(documentId, "No content extracted from document");
      }

      // Chunk the content
      List<String> chunks = chunkContent(content);
      log.info("Document {} split into {} chunks", documentId, chunks.size());

      // Extract document metadata
      String documentTitle = metadataExtractor.extractTitle(content, document.getFileName());
      List<String> documentKeywords =
          ragConfig.getMetadata().isExtractKeywords()
              ? metadataExtractor.extractKeywords(content)
              : List.of();
      log.debug("Extracted metadata - title: {}, keywords: {}", documentTitle, documentKeywords);

      // Build document chunks with metadata
      List<DocumentChunk> documentChunks = new ArrayList<>();
      List<String> textsToEmbed = new ArrayList<>();
      int charOffset = 0;

      for (int i = 0; i < chunks.size(); i++) {
        String chunkContent = chunks.get(i);

        // Find the section this chunk belongs to
        String sectionTitle =
            ragConfig.getMetadata().isExtractSections()
                ? metadataExtractor.findChunkSection(content, charOffset)
                : null;

        // Extract chunk-specific keywords
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

        textsToEmbed.add(enrichedContent);

        documentChunks.add(
            DocumentChunk.builder()
                .id(documentId + "_" + i)
                .documentId(documentId)
                .sessionId(document.getSession().getId())
                .fileName(document.getFileName())
                .chunkIndex(i)
                .content(chunkContent)
                .documentTitle(documentTitle)
                .sectionTitle(sectionTitle)
                .keywords(chunkKeywords)
                .enrichedContent(enrichedContent)
                .tokenCount(estimateTokenCount(chunkContent))
                .build());

        charOffset += chunkContent.length();
      }

      // Generate embeddings for enriched content (or raw content if not enriched)
      log.debug("Generating embeddings for {} chunks...", textsToEmbed.size());
      List<List<Float>> embeddings = embeddingService.embedTexts(textsToEmbed);
      log.debug(
          "Generated {} embeddings, first embedding size: {}",
          embeddings.size(),
          embeddings.isEmpty() ? 0 : embeddings.get(0).size());

      // Attach embeddings to chunks
      for (int i = 0; i < documentChunks.size(); i++) {
        List<Float> embedding = i < embeddings.size() ? embeddings.get(i) : List.of();
        documentChunks.get(i).setEmbedding(embedding);
      }

      // Index chunks in Elasticsearch
      log.debug("Indexing {} chunks to Elasticsearch...", documentChunks.size());
      elasticsearchIndexService.indexChunks(documentChunks);
      log.debug("Elasticsearch indexing complete for document {}", documentId);

      // Update document status to ready
      updateDocumentStatusWithRetry(documentId, DocumentStatus.READY, chunks.size(), null);

      meterRegistry.counter("document.processing.success").increment();
      log.info("Successfully processed document: {}", documentId);

    } catch (Exception e) {
      log.error("Failed to process document {}: {}", documentId, e.getMessage());
      meterRegistry.counter("document.processing.failure").increment();

      // Update status to failed with retry
      try {
        updateDocumentStatusWithRetry(documentId, DocumentStatus.FAILED, null, e.getMessage());
      } catch (Exception retryEx) {
        log.error("Failed to update document status after retries: {}", retryEx.getMessage());
      }
    } finally {
      sample.stop(meterRegistry.timer("document.processing.duration"));
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
        return; // Success
      } catch (CannotAcquireLockException e) {
        if (attempt == MAX_RETRIES) {
          log.error("Failed to update document {} after {} retries", documentId, MAX_RETRIES);
          throw e;
        }
        log.warn(
            "SQLite lock contention on document {}, retry {}/{}", documentId, attempt, MAX_RETRIES);
        try {
          Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted during retry", ie);
        }
      }
    }
  }

  private String parseDocument(InputStream inputStream) {
    try {
      return tika.parseToString(inputStream);
    } catch (Exception e) {
      log.error("Tika parsing failed: {}", e.getMessage());
      throw new DocumentProcessingException(null, "Failed to parse document: " + e.getMessage());
    }
  }

  /**
   * Chunks content using a sliding window approach.
   *
   * @param content the full document content
   * @return list of content chunks
   */
  private List<String> chunkContent(String content) {
    List<String> chunks = new ArrayList<>();
    int chunkSize = ragConfig.getChunking().getSize();
    int overlap = ragConfig.getChunking().getOverlap();

    // Split by paragraphs first for better semantic boundaries
    String[] paragraphs = content.split("\\n\\n+");
    StringBuilder currentChunk = new StringBuilder();
    int currentTokens = 0;

    for (String paragraph : paragraphs) {
      int paragraphTokens = estimateTokenCount(paragraph);

      if (currentTokens + paragraphTokens > chunkSize && currentChunk.length() > 0) {
        // Save current chunk
        chunks.add(currentChunk.toString().trim());

        // Start new chunk with overlap
        String overlapText = getOverlapText(currentChunk.toString(), overlap);
        currentChunk = new StringBuilder(overlapText);
        currentTokens = estimateTokenCount(overlapText);
      }

      currentChunk.append(paragraph).append("\n\n");
      currentTokens += paragraphTokens;
    }

    // Add final chunk
    if (currentChunk.length() > 0) {
      chunks.add(currentChunk.toString().trim());
    }

    return chunks;
  }

  private String getOverlapText(String text, int overlapTokens) {
    String[] words = text.split("\\s+");
    int wordsToKeep = Math.min(overlapTokens, words.length);
    StringBuilder overlap = new StringBuilder();
    for (int i = words.length - wordsToKeep; i < words.length; i++) {
      overlap.append(words[i]).append(" ");
    }
    return overlap.toString();
  }

  /** Estimates token count (roughly 4 characters per token for English). */
  private int estimateTokenCount(String text) {
    return text.length() / 4;
  }
}
