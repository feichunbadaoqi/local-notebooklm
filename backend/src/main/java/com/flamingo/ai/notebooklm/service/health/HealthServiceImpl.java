package com.flamingo.ai.notebooklm.service.health;

import com.flamingo.ai.notebooklm.api.dto.response.SystemStats;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.domain.repository.SessionRepository;
import io.micrometer.core.annotation.Timed;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of HealthService for system health checks and statistics. */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthServiceImpl implements HealthService {

  private final SessionRepository sessionRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final DocumentRepository documentRepository;

  @Override
  @Transactional(readOnly = true)
  @Timed(value = "health.stats", description = "Time to get system stats")
  public SystemStats getSystemStats() {
    long totalSessions = sessionRepository.count();
    long totalMessages = sessionRepository.countTotalMessages();
    long totalDocuments = documentRepository.count();

    return SystemStats.builder()
        .totalSessions(totalSessions)
        .totalMessages(totalMessages)
        .totalDocuments(totalDocuments)
        .timestamp(LocalDateTime.now())
        .build();
  }
}
