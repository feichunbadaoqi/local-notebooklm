package com.flamingo.ai.notebooklm.config;

import com.flamingo.ai.notebooklm.domain.entity.Memory;
import com.flamingo.ai.notebooklm.domain.repository.MemoryRepository;
import com.flamingo.ai.notebooklm.elasticsearch.MemoryDocument;
import com.flamingo.ai.notebooklm.elasticsearch.MemoryIndexService;
import com.flamingo.ai.notebooklm.service.rag.EmbeddingService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Startup bean to backfill embeddings for existing memories.
 *
 * <p>This bean runs once on application startup and:
 *
 * <ul>
 *   <li>Checks if there are memories in SQLite without Elasticsearch index
 *   <li>Generates embeddings for all existing memories
 *   <li>Indexes them to Elasticsearch for semantic search
 * </ul>
 *
 * <p>This ensures backward compatibility for memories created before Phase 4 implementation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryBackfillStartupBean implements CommandLineRunner {

  private final MemoryRepository memoryRepository;
  private final MemoryIndexService memoryIndexService;
  private final EmbeddingService embeddingService;

  @Override
  public void run(String... args) {
    try {
      log.info("Checking for memories to backfill...");

      List<Memory> allMemories = memoryRepository.findAll();
      if (allMemories.isEmpty()) {
        log.info("No memories found, skipping backfill");
        return;
      }

      log.info("Found {} memories, starting backfill process", allMemories.size());

      List<MemoryDocument> documentsToIndex = new ArrayList<>();
      int processed = 0;

      for (Memory memory : allMemories) {
        try {
          // Generate embedding for memory content
          List<Float> embedding = embeddingService.embedText(memory.getMemoryContent());

          MemoryDocument doc =
              MemoryDocument.builder()
                  .id(memory.getId().toString())
                  .sessionId(memory.getSession().getId())
                  .memoryContent(memory.getMemoryContent())
                  .memoryType(memory.getMemoryType())
                  .importance(memory.getImportance())
                  .embedding(embedding)
                  .timestamp(System.currentTimeMillis())
                  .build();

          documentsToIndex.add(doc);
          processed++;

          // Batch index every 50 memories to avoid overwhelming ES
          if (documentsToIndex.size() >= 50) {
            memoryIndexService.indexMemories(documentsToIndex);
            log.info(
                "Indexed batch of {} memories ({}/{})",
                documentsToIndex.size(),
                processed,
                allMemories.size());
            documentsToIndex.clear();
          }

        } catch (Exception e) {
          log.warn(
              "Failed to generate embedding for memory {}: {}", memory.getId(), e.getMessage());
        }
      }

      // Index remaining memories
      if (!documentsToIndex.isEmpty()) {
        memoryIndexService.indexMemories(documentsToIndex);
        log.info("Indexed final batch of {} memories", documentsToIndex.size());
      }

      // Refresh index to make all documents searchable
      memoryIndexService.refresh();

      log.info(
          "Memory backfill complete: successfully indexed {}/{} memories",
          processed,
          allMemories.size());

    } catch (Exception e) {
      log.error("Memory backfill failed: {}", e.getMessage(), e);
      // Don't fail application startup if backfill fails
    }
  }
}
