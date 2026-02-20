package com.flamingo.ai.notebooklm.service.rag;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Routes a document MIME type to the highest-priority {@link DocumentChunkingStrategy} that
 * supports it.
 *
 * <p>Strategies are injected by Spring in {@code @Order} order (ascending). The router picks the
 * first strategy that returns {@code true} for {@code supports(mimeType)}.
 *
 * <p>{@link com.flamingo.ai.notebooklm.service.rag.DocumentProcessingService} depends exclusively
 * on this router — it never references concrete strategy implementations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentChunkingStrategyRouter {

  private final List<DocumentChunkingStrategy> strategies;

  /**
   * Returns the highest-priority strategy that supports the given MIME type.
   *
   * @param mimeType document MIME type (e.g. {@code application/pdf})
   * @return selected strategy (never null; {@link TikaPlainTextChunkingStrategy} is the catch-all)
   * @throws IllegalStateException if no strategy supports the MIME type (should not happen)
   */
  public DocumentChunkingStrategy route(String mimeType) {
    return strategies.stream()
        .filter(s -> s.supports(mimeType))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No DocumentChunkingStrategy found for MIME type: " + mimeType));
  }
}
