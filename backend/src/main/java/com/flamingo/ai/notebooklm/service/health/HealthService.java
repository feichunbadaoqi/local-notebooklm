package com.flamingo.ai.notebooklm.service.health;

import com.flamingo.ai.notebooklm.api.dto.response.SystemStats;

/** Service interface for health checks and system statistics. */
public interface HealthService {

  /**
   * Gets system-wide statistics including total sessions, messages, and documents.
   *
   * @return system statistics
   */
  SystemStats getSystemStats();
}
