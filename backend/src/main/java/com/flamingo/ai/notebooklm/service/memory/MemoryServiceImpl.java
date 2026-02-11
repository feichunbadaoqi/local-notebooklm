package com.flamingo.ai.notebooklm.service.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.domain.entity.Memory;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.repository.MemoryRepository;
import com.flamingo.ai.notebooklm.exception.MemoryNotFoundException;
import com.flamingo.ai.notebooklm.service.session.SessionService;
import dev.langchain4j.model.chat.ChatModel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
  private final ChatModel chatModel;
  private final RagConfig ragConfig;
  private final MeterRegistry meterRegistry;
  private final ObjectMapper objectMapper;

  private static final String EXTRACTION_PROMPT_TEMPLATE =
      """
      You are a memory extraction assistant. Analyze the following conversation exchange
      and extract important facts, user preferences, or insights worth remembering.

      User message: %s
      Assistant response: %s

      Extract memories in JSON format (return ONLY the JSON array, no other text):
      [
        {"type": "fact|preference|insight", "content": "...", "importance": 0.0-1.0}
      ]

      Rules:
      - Only extract genuinely important information worth remembering long-term
      - Facts: specific data points, dates, names, numbers from documents
      - Preferences: how the user likes information presented or what they focus on
      - Insights: connections, conclusions, or patterns discovered
      - Importance: 0.0 (trivial) to 1.0 (critical)
      - Return empty array [] if nothing worth remembering
      - Keep each memory concise (1-2 sentences max)
      - Return ONLY valid JSON, no markdown or explanation
      """;

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
    String prompt = String.format(EXTRACTION_PROMPT_TEMPLATE, userMessage, assistantResponse);

    String response = chatModel.chat(prompt);

    return parseExtractedMemories(response);
  }

  @SuppressWarnings("unused")
  private List<ExtractedMemory> extractMemoriesFallback(
      String userMessage, String assistantResponse, Throwable t) {
    log.warn("Memory extraction fallback triggered: {}", t.getMessage());
    return List.of();
  }

  private List<ExtractedMemory> parseExtractedMemories(String response) {
    try {
      // Clean up response - remove markdown code blocks if present
      String cleaned = response.trim();
      if (cleaned.startsWith("```json")) {
        cleaned = cleaned.substring(7);
      } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.substring(3);
      }
      if (cleaned.endsWith("```")) {
        cleaned = cleaned.substring(0, cleaned.length() - 3);
      }
      cleaned = cleaned.trim();

      List<RawMemory> rawMemories =
          objectMapper.readValue(cleaned, new TypeReference<List<RawMemory>>() {});

      List<ExtractedMemory> result = new ArrayList<>();
      for (RawMemory raw : rawMemories) {
        if (isValidMemoryType(raw.type()) && raw.content() != null && !raw.content().isBlank()) {
          float importance = raw.importance() != null ? raw.importance() : 0.5f;
          importance = Math.max(0.0f, Math.min(1.0f, importance));
          result.add(
              new ExtractedMemory(raw.type().toLowerCase(Locale.ROOT), raw.content(), importance));
        }
      }
      return result;

    } catch (JsonProcessingException e) {
      log.warn("Failed to parse memory extraction response: {}", e.getMessage());
      return List.of();
    }
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

    memoryRepository.save(memory);
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

    // For now, return top memories by importance
    // Future: use embedding similarity to find query-relevant memories
    List<Memory> memories =
        memoryRepository.findTopMemoriesBySessionId(sessionId, PageRequest.of(0, limit));

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

  @Override
  @Transactional(readOnly = true)
  public List<Memory> getAllMemories(UUID sessionId) {
    sessionService.getSession(sessionId); // Validate session exists
    return memoryRepository.findBySessionIdOrderByImportanceDesc(sessionId);
  }

  @Override
  @Transactional(readOnly = true)
  public Memory getMemory(UUID memoryId) {
    return memoryRepository
        .findById(memoryId)
        .orElseThrow(() -> new MemoryNotFoundException(memoryId));
  }

  @Override
  @Transactional
  public void deleteMemory(UUID memoryId) {
    if (!memoryRepository.existsById(memoryId)) {
      throw new MemoryNotFoundException(memoryId);
    }
    memoryRepository.deleteById(memoryId);
    log.info("Deleted memory {}", memoryId);
  }

  @Override
  @Transactional
  public Memory addMemory(UUID sessionId, String content, String type, Float importance) {
    Session session = sessionService.getSession(sessionId);

    if (!isValidMemoryType(type)) {
      throw new IllegalArgumentException("Invalid memory type: " + type);
    }

    float finalImportance = importance != null ? importance : 0.5f;
    finalImportance = Math.max(0.0f, Math.min(1.0f, finalImportance));

    Memory memory =
        Memory.builder()
            .session(session)
            .memoryContent(content)
            .memoryType(type.toLowerCase(Locale.ROOT))
            .importance(finalImportance)
            .build();

    Memory saved = memoryRepository.save(memory);
    log.info("Manually added memory {} for session {}", saved.getId(), sessionId);

    enforceMaxMemories(sessionId);

    return saved;
  }

  /** Record for raw JSON parsing. */
  private record RawMemory(String type, String content, Float importance) {}

  /** Record for validated extracted memory. */
  private record ExtractedMemory(String type, String content, float importance) {}
}
