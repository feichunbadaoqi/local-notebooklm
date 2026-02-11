package com.flamingo.ai.notebooklm.service.session;

import com.flamingo.ai.notebooklm.api.dto.request.CreateSessionRequest;
import com.flamingo.ai.notebooklm.api.dto.request.UpdateSessionRequest;
import com.flamingo.ai.notebooklm.domain.entity.Session;
import com.flamingo.ai.notebooklm.domain.enums.InteractionMode;
import java.util.List;
import java.util.UUID;

/** Service interface for session management. */
public interface SessionService {

  /**
   * Creates a new session.
   *
   * @param request the create session request
   * @return the created session
   */
  Session createSession(CreateSessionRequest request);

  /**
   * Gets a session by ID.
   *
   * @param sessionId the session ID
   * @return the session
   * @throws com.flamingo.ai.notebooklm.exception.SessionNotFoundException if not found
   */
  Session getSession(UUID sessionId);

  /**
   * Gets all sessions ordered by last accessed time.
   *
   * @return list of all sessions
   */
  List<Session> getAllSessions();

  /**
   * Updates a session.
   *
   * @param sessionId the session ID
   * @param request the update request
   * @return the updated session
   */
  Session updateSession(UUID sessionId, UpdateSessionRequest request);

  /**
   * Updates the interaction mode of a session.
   *
   * @param sessionId the session ID
   * @param mode the new interaction mode
   * @return the updated session
   */
  Session updateSessionMode(UUID sessionId, InteractionMode mode);

  /**
   * Deletes a session and all associated data.
   *
   * @param sessionId the session ID
   */
  void deleteSession(UUID sessionId);

  /**
   * Touches a session to update its last accessed time.
   *
   * @param sessionId the session ID
   * @return the updated session
   */
  Session touchSession(UUID sessionId);
}
