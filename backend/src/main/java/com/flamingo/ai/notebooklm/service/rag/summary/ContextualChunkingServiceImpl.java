package com.flamingo.ai.notebooklm.service.rag.summary;

import com.flamingo.ai.notebooklm.agent.ContextualChunkingAgent;
import com.flamingo.ai.notebooklm.config.RagConfig;
import com.flamingo.ai.notebooklm.elasticsearch.DocumentChunk;
import io.micrometer.core.annotation.Timed;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Implementation of {@link ContextualChunkingService} using an LLM agent. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContextualChunkingServiceImpl implements ContextualChunkingService {

  private final ContextualChunkingAgent contextualChunkingAgent;
  private final RagConfig ragConfig;

  @Override
  @Timed(
      value = "document.contextual_chunking",
      description = "Time to generate contextual prefixes")
  public void generatePrefixes(
      List<DocumentChunk> chunks, String documentSummary, List<String> textsToEmbed) {
    if (!ragConfig.getContextualChunking().isEnabled()) {
      return;
    }

    if (documentSummary == null || documentSummary.isBlank()) {
      log.warn("Skipping contextual chunking: no document summary available");
      return;
    }

    int maxSummaryChars = ragConfig.getContextualChunking().getMaxSummaryChars();
    String truncatedSummary =
        documentSummary.length() > maxSummaryChars
            ? documentSummary.substring(0, maxSummaryChars)
            : documentSummary;

    log.info("Generating contextual prefixes for {} chunks", chunks.size());
    int successCount = 0;

    for (int i = 0; i < chunks.size(); i++) {
      DocumentChunk chunk = chunks.get(i);
      try {
        String prefix =
            contextualChunkingAgent.generatePrefix(truncatedSummary, chunk.getContent());
        if (prefix != null && !prefix.isBlank()) {
          chunk.setContextPrefix(prefix);
          chunk.setEnrichedContent(prefix + "\n\n" + chunk.getContent());
          textsToEmbed.set(i, prefix + "\n\n" + textsToEmbed.get(i));
          successCount++;
        }
      } catch (Exception e) {
        log.warn("Failed to generate contextual prefix for chunk {}: {}", i, e.getMessage());
      }
    }

    log.info("Generated contextual prefixes for {}/{} chunks", successCount, chunks.size());
  }
}
