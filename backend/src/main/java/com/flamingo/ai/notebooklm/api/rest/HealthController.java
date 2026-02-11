package com.flamingo.ai.notebooklm.api.rest;

import com.flamingo.ai.notebooklm.domain.repository.SessionRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for health checks and system info. */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

  private final SessionRepository sessionRepository;

  /** Returns a simple health check response. */
  @GetMapping
  public ResponseEntity<Map<String, Object>> health() {
    Map<String, Object> health = new HashMap<>();
    health.put("status", "UP");
    health.put("timestamp", LocalDateTime.now());
    health.put("service", "notebooklm");
    return ResponseEntity.ok(health);
  }

  /** Returns system statistics. */
  @GetMapping("/stats")
  public ResponseEntity<Map<String, Object>> stats() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("totalSessions", sessionRepository.count());
    stats.put("totalMessages", sessionRepository.countTotalMessages());
    stats.put("timestamp", LocalDateTime.now());
    return ResponseEntity.ok(stats);
  }
}
