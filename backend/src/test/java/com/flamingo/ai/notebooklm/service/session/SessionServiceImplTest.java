package com.flamingo.ai.notebooklm.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flamingo.ai.notebooklm.api.dto.request.CreateSessionRequest;
import com.flamingo.ai.notebooklm.api.dto.request.UpdateSessionRequest;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import com.flamingo.ai.notebooklm.domain.repository.ChatMessageRepository;
import com.flamingo.ai.notebooklm.domain.repository.DocumentRepository;
import com.flamingo.ai.notebooklm.domain.repository.SessionRepository;
import com.flamingo.ai.notebooklm.exception.SessionNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionServiceImplTest {

  @Mock private SessionRepository sessionRepository;

  @Mock private DocumentRepository documentRepository;

  @Mock private ChatMessageRepository chatMessageRepository;

  @Mock private MeterRegistry meterRegistry;

  @Mock private Counter counter;

  private SessionServiceImpl sessionService;

  @BeforeEach
  void setUp() {
    sessionService =
        new SessionServiceImpl(
            sessionRepository, documentRepository, chatMessageRepository, meterRegistry);
    when(meterRegistry.counter(any(String.class))).thenReturn(counter);
    when(meterRegistry.counter(any(String.class), any(String.class), any(String.class)))
        .thenReturn(counter);
  }

  @Test
  void shouldCreateSession_whenValidRequest() {
    // Given
    CreateSessionRequest request =
        CreateSessionRequest.builder().title("Test Session").mode(InteractionMode.RESEARCH).build();

    Session savedSession =
        Session.builder()
            .id(UUID.randomUUID())
            .title("Test Session")
            .currentMode(InteractionMode.RESEARCH)
            .build();

    when(sessionRepository.save(any(Session.class))).thenReturn(savedSession);

    // When
    Session result = sessionService.createSession(request);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getTitle()).isEqualTo("Test Session");
    assertThat(result.getCurrentMode()).isEqualTo(InteractionMode.RESEARCH);
    verify(sessionRepository).save(any(Session.class));
    verify(counter).increment();
  }

  @Test
  void shouldCreateSession_withDefaultMode_whenModeNotProvided() {
    // Given
    CreateSessionRequest request = CreateSessionRequest.builder().title("Test Session").build();

    Session savedSession =
        Session.builder()
            .id(UUID.randomUUID())
            .title("Test Session")
            .currentMode(InteractionMode.EXPLORING)
            .build();

    when(sessionRepository.save(any(Session.class))).thenReturn(savedSession);

    // When
    Session result = sessionService.createSession(request);

    // Then
    assertThat(result.getCurrentMode()).isEqualTo(InteractionMode.EXPLORING);
  }

  @Test
  void shouldGetSession_whenSessionExists() {
    // Given
    UUID sessionId = UUID.randomUUID();
    Session session =
        Session.builder()
            .id(sessionId)
            .title("Test Session")
            .currentMode(InteractionMode.EXPLORING)
            .build();

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    // When
    Session result = sessionService.getSession(sessionId);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(sessionId);
  }

  @Test
  void shouldThrowException_whenSessionNotFound() {
    // Given
    UUID sessionId = UUID.randomUUID();
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

    // When/Then
    assertThatThrownBy(() -> sessionService.getSession(sessionId))
        .isInstanceOf(SessionNotFoundException.class);
  }

  @Test
  void shouldGetAllSessions_orderedByLastAccessed() {
    // Given
    Session session1 =
        Session.builder()
            .id(UUID.randomUUID())
            .title("Session 1")
            .currentMode(InteractionMode.EXPLORING)
            .build();

    Session session2 =
        Session.builder()
            .id(UUID.randomUUID())
            .title("Session 2")
            .currentMode(InteractionMode.RESEARCH)
            .build();

    when(sessionRepository.findAllByOrderByLastAccessedAtDesc())
        .thenReturn(List.of(session2, session1));

    // When
    List<Session> result = sessionService.getAllSessions();

    // Then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getTitle()).isEqualTo("Session 2");
  }

  @Test
  void shouldUpdateSession_whenTitleProvided() {
    // Given
    UUID sessionId = UUID.randomUUID();
    Session existingSession =
        Session.builder()
            .id(sessionId)
            .title("Old Title")
            .currentMode(InteractionMode.EXPLORING)
            .build();

    UpdateSessionRequest request = UpdateSessionRequest.builder().title("New Title").build();

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession));
    when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

    // When
    Session result = sessionService.updateSession(sessionId, request);

    // Then
    assertThat(result.getTitle()).isEqualTo("New Title");
  }

  @Test
  void shouldUpdateSessionMode() {
    // Given
    UUID sessionId = UUID.randomUUID();
    Session existingSession =
        Session.builder()
            .id(sessionId)
            .title("Test Session")
            .currentMode(InteractionMode.EXPLORING)
            .build();

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(existingSession));
    when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

    // When
    Session result = sessionService.updateSessionMode(sessionId, InteractionMode.LEARNING);

    // Then
    assertThat(result.getCurrentMode()).isEqualTo(InteractionMode.LEARNING);
  }

  @Test
  void shouldDeleteSession() {
    // Given
    UUID sessionId = UUID.randomUUID();
    Session session =
        Session.builder()
            .id(sessionId)
            .title("Test Session")
            .currentMode(InteractionMode.EXPLORING)
            .build();

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    // When
    sessionService.deleteSession(sessionId);

    // Then
    verify(sessionRepository).delete(session);
    verify(counter).increment();
  }

  @Test
  void shouldTouchSession() {
    // Given
    UUID sessionId = UUID.randomUUID();
    LocalDateTime originalTime = LocalDateTime.now().minusHours(1);
    Session session =
        Session.builder()
            .id(sessionId)
            .title("Test Session")
            .currentMode(InteractionMode.EXPLORING)
            .build();
    session.setLastAccessedAt(originalTime);

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

    // When
    Session result = sessionService.touchSession(sessionId);

    // Then
    assertThat(result.getLastAccessedAt()).isAfterOrEqualTo(originalTime);
    verify(sessionRepository).save(session);
  }
}
