package com.flamingo.ai.notebooklm.service.memory;

import com.flamingo.ai.notebooklm.agent.MemoryExtractionAgent;
import com.flamingo.ai.notebooklm.agent.dto.ExtractedMemory;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.Memory;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.repository.MemoryRepository;
import com.flamingo.ai.notebooklm.elasticsearch.MemoryDocument;
import com.flamingo.ai.notebooklm.elasticsearch.MemoryIndexService;
import com.flamingo.ai.notebooklm.service.rag.EmbeddingService;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of MemoryService for extracting and managing conversation memories. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryServiceImpl implements MemoryService {

  private final MemoryRepository memoryRepository;
  private final SessionService sessionService;
  private final MemoryExtractionAgent agent;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;
  private final MemoryIndexService memoryIndexService;
  private final EmbeddingService embeddingService;
  private final MemoryHybridSearchService memoryHybridSearchService;

  @Override
  @Async("documentProcessingExecutor")
  public void extractAndSaveAsync(
      UUID sessionId, String userMessage, String assistantResponse, InteractionMode mode) {

    if (!ragConfig.getMemory().isEnabled()) {
      return;
    }

    meterRegistry.counter("memory.extraction.count").increment();

    try {
      List<ExtractedMemory> extracted = extractMemories(userMessage, assistantResponse);

      if (extracted.isEmpty()) {
        log.debug("No memories extracted for session {}", sessionId);
        return;
      }

      Session session = sessionService.getSession(sessionId);
      int savedCount = 0;

      for (ExtractedMemory em : extracted) {
        if (em.importance() >= ragConfig.getMemory().getExtractionThreshold()) {
          if (!isDuplicate(sessionId, em.content())) {
            saveMemory(session, em.content(), em.type(), em.importance());
            savedCount++;
          }
        }
      }

      if (savedCount > 0) {
        meterRegistry.counter("memory.saved.count").increment(savedCount);
        log.info("Saved {} memories for session {}", savedCount, sessionId);
      }

      enforceMaxMemories(sessionId);
      meterRegistry.counter("memory.extraction.success").increment();

    } catch (Exception e) {
      log.error("Failed to extract memories for session {}: {}", sessionId, e.getMessage());
      meterRegistry.counter("memory.extraction.errors").increment();
    }
  }

  @CircuitBreaker(name = "openai", fallbackMethod = "extractMemoriesFallback")
  private List<ExtractedMemory> extractMemories(String userMessage, String assistantResponse) {
    return agent.extract(userMessage, assistantResponse);
  }

  @SuppressWarnings("unused")
  private List<ExtractedMemory> extractMemoriesFallback(
      String userMessage, String assistantResponse, Throwable t) {
    log.warn("Memory extraction fallback triggered: {}", t.getMessage());
    return List.of();
  }

  private boolean isValidMemoryType(String type) {
    return type != null
        && (type.equalsIgnoreCase(Memory.TYPE_FACT)
            || type.equalsIgnoreCase(Memory.TYPE_PREFERENCE)
            || type.equalsIgnoreCase(Memory.TYPE_INSIGHT));
  }

  private boolean isDuplicate(UUID sessionId, String content) {
    List<Memory> existing = memoryRepository.findBySessionIdOrderByImportanceDesc(sessionId);

    String normalizedContent = content.toLowerCase(Locale.ROOT).trim();
    for (Memory memory : existing) {
      String normalizedExisting = memory.getMemoryContent().toLowerCase(Locale.ROOT).trim();

      // Exact match
      if (normalizedContent.equals(normalizedExisting)) {
        return true;
      }

      // High similarity check (simple containment for now)
      if (normalizedContent.contains(normalizedExisting)
          || normalizedExisting.contains(normalizedContent)) {
        // Increase importance of existing memory
        memory.increaseImportance(0.1f);
        memoryRepository.save(memory);
        return true;
      }
    }

    return false;
  }

  private void saveMemory(Session session, String content, String type, float importance) {
    Memory memory =
        Memory.builder()
            .session(session)
            .memoryContent(content)
            .memoryType(type)
            .importance(importance)
            .build();

    Memory saved = memoryRepository.save(memory);

    // Index to Elasticsearch asynchronously for semantic search
    try {
      List<Float> embedding = embeddingService.embedText(content);
      MemoryDocument doc =
          MemoryDocument.builder()
              .id(saved.getId().toString())
              .sessionId(session.getId())
              .memoryContent(content)
              .memoryType(type)
              .importance(importance)
              .embedding(embedding)
              .timestamp(System.currentTimeMillis())
              .build();

      memoryIndexService.indexMemories(List.of(doc));
      log.debug("Indexed memory {} to Elasticsearch", saved.getId());
    } catch (Exception e) {
      // Don't fail the request if ES indexing fails
      log.warn("Failed to index memory {} to Elasticsearch: {}", saved.getId(), e.getMessage());
    }
  }

  private void enforceMaxMemories(UUID sessionId) {
    int maxMemories = ragConfig.getMemory().getMaxPerSession();
    long count = memoryRepository.countBySessionId(sessionId);

    if (count > maxMemories) {
      // Get memories ordered by importance (ascending) and delete lowest
      List<Memory> allMemories = memoryRepository.findBySessionIdOrderByImportanceDesc(sessionId);
      int toDelete = (int) (count - maxMemories);

      for (int i = allMemories.size() - 1; i >= allMemories.size() - toDelete && i >= 0; i--) {
        memoryRepository.delete(allMemories.get(i));
      }

      log.info("Pruned {} low-importance memories for session {}", toDelete, sessionId);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<Memory> getRelevantMemories(UUID sessionId, String query, int limit) {
    sessionService.getSession(sessionId); // Validate session exists

    // Use hybrid search to find semantically relevant memories
    List<MemoryDocument> relevantDocs = memoryHybridSearchService.search(sessionId, query, limit);

    if (relevantDocs.isEmpty()) {
      return List.of();
    }

    // Convert to Memory entities (fetch from SQLite by ID)
    List<Memory> memories =
        relevantDocs.stream()
            .map(doc -> memoryRepository.findById(UUID.fromString(doc.getId())))
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .toList();

    // Touch accessed memories
    for (Memory memory : memories) {
      memory.touch();
    }
    if (!memories.isEmpty()) {
      memoryRepository.saveAll(memories);
      meterRegistry.counter("memory.retrieved.count").increment(memories.size());
    }

    return memories;
  }

  @Override
  public String buildMemoryContext(List<Memory> memories) {
    if (memories.isEmpty()) {
      return "";
    }

    StringBuilder context = new StringBuilder();
    context.append("Relevant memories from this session:\n");

    for (Memory memory : memories) {
      String typeLabel = memory.getMemoryType().toUpperCase(Locale.ROOT);
      context.append(
          String.format(
              "- [%s] %s (importance: %.1f)%n",
              typeLabel, memory.getMemoryContent(), memory.getImportance()));
    }

    context.append("\nUse these memories to provide contextually aware responses.");

    return context.toString();
  }
}
