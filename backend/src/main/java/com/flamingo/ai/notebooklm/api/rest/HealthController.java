package com.flamingo.ai.notebooklm.api.rest;

import com.flamingo.ai.notebooklm.api.dto.response.SystemStats;
import com.flamingo.ai.notebooklm.service.health.HealthService;
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

  private final HealthService healthService;

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
  public ResponseEntity<SystemStats> stats() {
    SystemStats systemStats = healthService.getSystemStats();
    return ResponseEntity.ok(systemStats);
  }
}
