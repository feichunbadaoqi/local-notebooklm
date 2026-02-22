package com.flamingo.ai.notebooklm.service.rag.topic;

import com.flamingo.ai.notebooklm.domain.entity.Document;
import com.flamingo.ai.notebooklm.domain.enums.DocumentStatus;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of {@link TopicIndexService} that builds a topic index from READY documents. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TopicIndexServiceImpl implements TopicIndexService {

  private final DocumentRepository documentRepository;

  @Override
  @Transactional(readOnly = true)
  public String buildTopicIndex(UUID sessionId, InteractionMode mode) {
    List<Document> documents =
        documentRepository.findBySessionIdAndStatus(sessionId, DocumentStatus.READY);

    if (documents.isEmpty()) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    boolean hasTopics = false;

    for (Document doc : documents) {
      List<String> topics = doc.getTopics();
      if (topics == null || topics.isEmpty()) {
        continue;
      }
      if (!hasTopics) {
        sb.append("=== TOPIC INDEX (topics covered in your documents) ===\n");
        hasTopics = true;
      }
      sb.append(doc.getFileName()).append(":\n");
      for (String topic : topics) {
        sb.append("- ").append(topic).append("\n");
      }
      sb.append("\n");
    }

    if (!hasTopics) {
      return "";
    }

    sb.append(getModeInstruction(mode));

    log.debug("Built topic index for session {} ({} chars)", sessionId, sb.length());
    return sb.toString();
  }

  private String getModeInstruction(InteractionMode mode) {
    return switch (mode) {
      case EXPLORING ->
          "When suggesting follow-up topics, ONLY suggest from the index above. "
              + "Do not suggest topics not covered in the documents.";
      case RESEARCH ->
          "The topic index shows what is covered. Focus responses on documented areas.";
      case LEARNING ->
          "Use the topic index to guide learning. "
              + "Suggest next topics from available document topics.";
    };
  }
}
