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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for processing documents: parsing with Tika, chunking, and indexing. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

  private final DocumentRepository documentRepository;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final EmbeddingService embeddingService;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  private final Tika tika = new Tika();

  /**
   * Processes a document asynchronously: parse, chunk, embed, and index.
   *
   * @param documentId the document to process
   * @param inputStream the document content stream
   */
  @Async("documentProcessingExecutor")
  @Transactional
  public void processDocumentAsync(UUID documentId, InputStream inputStream) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      Document document =
          documentRepository
              .findById(documentId)
              .orElseThrow(() -> new DocumentProcessingException(documentId, "Document not found"));

      // Update status to processing
      document.setStatus(DocumentStatus.PROCESSING);
      documentRepository.save(document);

      // Parse document with Tika
      String content = parseDocument(inputStream);
      if (content == null || content.isBlank()) {
        throw new DocumentProcessingException(documentId, "No content extracted from document");
      }

      // Chunk the content
      List<String> chunks = chunkContent(content);
      log.info("Document {} split into {} chunks", documentId, chunks.size());

      // Generate embeddings for all chunks
      List<List<Float>> embeddings = embeddingService.embedTexts(chunks);

      // Create document chunks with embeddings
      List<DocumentChunk> documentChunks = new ArrayList<>();
      for (int i = 0; i < chunks.size(); i++) {
        String chunkContent = chunks.get(i);
        List<Float> embedding = i < embeddings.size() ? embeddings.get(i) : List.of();

        documentChunks.add(
            DocumentChunk.builder()
                .id(documentId + "_" + i)
                .documentId(documentId)
                .sessionId(document.getSession().getId())
                .fileName(document.getFileName())
                .chunkIndex(i)
                .content(chunkContent)
                .embedding(embedding)
                .tokenCount(estimateTokenCount(chunkContent))
                .build());
      }

      // Index chunks in Elasticsearch
      elasticsearchIndexService.indexChunks(documentChunks);

      // Update document status
      document.setStatus(DocumentStatus.READY);
      document.setChunkCount(chunks.size());
      documentRepository.save(document);

      meterRegistry.counter("document.processing.success").increment();
      log.info("Successfully processed document: {}", documentId);

    } catch (Exception e) {
      log.error("Failed to process document {}: {}", documentId, e.getMessage());
      meterRegistry.counter("document.processing.failure").increment();

      // Update status to failed
      documentRepository
          .findById(documentId)
          .ifPresent(
              doc -> {
                doc.markFailed(e.getMessage());
                documentRepository.save(doc);
              });
    } finally {
      sample.stop(meterRegistry.timer("document.processing.duration"));
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
