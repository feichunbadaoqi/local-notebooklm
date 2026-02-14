package com.flamingo.ai.notebooklm.service.session;

import com.flamingo.ai.notebooklm.api.dto.request.CreateSessionRequest;
import com.flamingo.ai.notebooklm.api.dto.request.UpdateSessionRequest;
import com.flamingo.ai.notebooklm.api.dto.response.SessionWithStats;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.domain.repository.SessionRepository;
import com.flamingo.ai.notebooklm.exception.SessionNotFoundException;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of the SessionService. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

  private final SessionRepository sessionRepository;
  private final DocumentRepository documentRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final MeterRegistry meterRegistry;

  @Override
  @Transactional
  @Timed(value = "session.create", description = "Time to create a session")
  public Session createSession(CreateSessionRequest request) {
    log.info("Creating new session with title: {}", request.getTitle());

    Session session =
        Session.builder()
            .title(request.getTitle())
            .currentMode(request.getMode() != null ? request.getMode() : InteractionMode.EXPLORING)
            .build();

    Session saved = sessionRepository.save(session);
    meterRegistry.counter("session.created").increment();

    log.info("Created session with ID: {}", saved.getId());
    return saved;
  }

  @Override
  @Transactional(readOnly = true)
  @Timed(value = "session.get", description = "Time to get a session")
  public Session getSession(UUID sessionId) {
    return sessionRepository
        .findById(sessionId)
        .orElseThrow(() -> new SessionNotFoundException(sessionId));
  }

  @Override
  @Transactional(readOnly = true)
  @Timed(value = "session.getAll", description = "Time to get all sessions")
  public List<Session> getAllSessions() {
    return sessionRepository.findAllByOrderByLastAccessedAtDesc();
  }

  @Override
  @Transactional
  @Timed(value = "session.update", description = "Time to update a session")
  public Session updateSession(UUID sessionId, UpdateSessionRequest request) {
    Session session = getSession(sessionId);

    if (request.getTitle() != null) {
      session.setTitle(request.getTitle());
    }
    if (request.getMode() != null) {
      session.setCurrentMode(request.getMode());
    }

    return sessionRepository.save(session);
  }

  @Override
  @Transactional
  @Timed(value = "session.updateMode", description = "Time to update session mode")
  public Session updateSessionMode(UUID sessionId, InteractionMode mode) {
    Session session = getSession(sessionId);
    session.setCurrentMode(mode);

    log.info("Updated session {} mode to {}", sessionId, mode);
    meterRegistry.counter("session.mode.changed", "mode", mode.name()).increment();

    return sessionRepository.save(session);
  }

  @Override
  @Transactional
  @Timed(value = "session.delete", description = "Time to delete a session")
  public void deleteSession(UUID sessionId) {
    Session session = getSession(sessionId);
    sessionRepository.delete(session);

    log.info("Deleted session: {}", sessionId);
    meterRegistry.counter("session.deleted").increment();
  }

  @Override
  @Transactional
  public Session touchSession(UUID sessionId) {
    Session session = getSession(sessionId);
    session.touch();
    return sessionRepository.save(session);
  }

  @Override
  @Transactional(readOnly = true)
  @Timed(value = "session.getWithStats", description = "Time to get session with stats")
  public SessionWithStats getSessionWithStats(UUID sessionId) {
    Session session = getSession(sessionId);
    long documentCount = documentRepository.countBySessionId(sessionId);
    long messageCount = chatMessageRepository.countBySessionId(sessionId);

    return SessionWithStats.builder()
        .session(session)
        .documentCount(documentCount)
        .messageCount(messageCount)
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  @Timed(value = "session.getAllWithStats", description = "Time to get all sessions with stats")
  public List<SessionWithStats> getAllSessionsWithStats() {
    List<Session> sessions = getAllSessions();

    return sessions.stream()
        .map(
            session ->
                SessionWithStats.builder()
                    .session(session)
                    .documentCount(documentRepository.countBySessionId(session.getId()))
                    .messageCount(chatMessageRepository.countBySessionId(session.getId()))
                    .build())
        .toList();
  }
}
