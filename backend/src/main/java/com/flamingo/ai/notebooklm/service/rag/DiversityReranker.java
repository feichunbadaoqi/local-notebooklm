package com.flamingo.ai.notebooklm.service.rag;

import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Diversity-aware reranking service for multi-document RAG retrieval.
 *
 * <p>Ensures search results include content from multiple documents using round-robin selection to
 * balance relevance with source diversity. This prevents a single highly-relevant document from
 * dominating all results.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiversityReranker {

  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;

  /**
   * Reranks chunks to ensure diversity across documents.
   *
   * <p>Algorithm:
   *
   * <ol>
   *   <li>Group chunks by document ID
   *   <li>Sort each group by relevance score (descending)
   *   <li>Round-robin select chunks from each document
   *   <li>Ensure minimum representation from each document (minChunksPerDocument)
   *   <li>Return top-K chunks with balanced diversity
   * </ol>
   *
   * @param chunks the candidate chunks to rerank (should be pre-sorted by relevance)
   * @param topK the number of chunks to return
   * @return diversity-reranked list of chunks
   */
  public List<DocumentChunk> rerank(List<DocumentChunk> chunks, int topK) {
    if (!ragConfig.getDiversity().isEnabled()) {
      log.debug("Diversity reranking disabled, returning original order");
      return chunks.stream().limit(topK).collect(Collectors.toList());
    }

    if (chunks == null || chunks.isEmpty()) {
      return List.of();
    }

    int minPerDoc = ragConfig.getDiversity().getMinChunksPerDocument();

    // Group chunks by document ID and sort each group by relevance
    Map<UUID, List<DocumentChunk>> byDocument =
        chunks.stream()
            .collect(
                Collectors.groupingBy(
                    DocumentChunk::getDocumentId,
                    LinkedHashMap::new,
                    Collectors.toCollection(ArrayList::new)));

    // Sort each group by relevance score (descending)
    byDocument
        .values()
        .forEach(
            list ->
                list.sort(Comparator.comparingDouble(DocumentChunk::getRelevanceScore).reversed()));

    int numDocuments = byDocument.size();
    log.debug(
        "Diversity reranking: {} chunks from {} documents, topK={}, minPerDoc={}",
        chunks.size(),
        numDocuments,
        topK,
        minPerDoc);

    // Track which documents have been exhausted beyond minimum
    Map<UUID, Integer> chunksTaken = new HashMap<>();
    byDocument.keySet().forEach(docId -> chunksTaken.put(docId, 0));

    List<DocumentChunk> result = new ArrayList<>();
    int round = 0;

    // Round-robin selection
    while (result.size() < topK && !byDocument.isEmpty()) {
      List<UUID> exhaustedDocs = new ArrayList<>();

      for (Map.Entry<UUID, List<DocumentChunk>> entry : byDocument.entrySet()) {
        UUID docId = entry.getKey();
        List<DocumentChunk> docChunks = entry.getValue();
        int taken = chunksTaken.get(docId);

        if (round < docChunks.size()) {
          result.add(docChunks.get(round));
          chunksTaken.put(docId, taken + 1);

          if (result.size() >= topK) {
            break;
          }
        } else if (taken >= minPerDoc) {
          // This document is exhausted and has met minimum, remove from rotation
          exhaustedDocs.add(docId);
        }
      }

      // Remove exhausted documents
      exhaustedDocs.forEach(byDocument::remove);
      round++;

      // Safety check to prevent infinite loop
      if (round > chunks.size()) {
        log.warn("Diversity reranking hit safety limit, breaking loop");
        break;
      }
    }

    // Record diversity metrics
    long uniqueDocuments = result.stream().map(DocumentChunk::getDocumentId).distinct().count();
    double diversityScore = result.isEmpty() ? 0 : (double) uniqueDocuments / result.size();

    meterRegistry.gauge("rag.rerank.documents.represented", uniqueDocuments);
    meterRegistry.gauge("rag.rerank.diversity.score", diversityScore);

    log.debug(
        "Diversity reranking complete: {} chunks from {} documents (diversity score: {:.2f})",
        result.size(),
        uniqueDocuments,
        diversityScore);

    return result;
  }

  /**
   * Calculates a diversity score for a set of chunks.
   *
   * @param chunks the chunks to evaluate
   * @return diversity score between 0.0 (all from one document) and 1.0 (one per document)
   */
  public double calculateDiversityScore(List<DocumentChunk> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return 0.0;
    }

    long uniqueDocuments = chunks.stream().map(DocumentChunk::getDocumentId).distinct().count();
    return (double) uniqueDocuments / chunks.size();
  }

  /**
   * Gets statistics about document representation in chunks.
   *
   * @param chunks the chunks to analyze
   * @return map of document ID to chunk count
   */
  public Map<UUID, Long> getDocumentDistribution(List<DocumentChunk> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return Map.of();
    }

    return chunks.stream()
        .collect(Collectors.groupingBy(DocumentChunk::getDocumentId, Collectors.counting()));
  }
}
