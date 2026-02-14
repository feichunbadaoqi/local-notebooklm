package com.flamingo.ai.notebooklm.api.rest;

import com.flamingo.ai.notebooklm.api.dto.request.CreateMemoryRequest;
import com.flamingo.ai.notebooklm.api.dto.response.MemoryResponse;
import com.flamingo.ai.notebooklm.domain.entity.Memory;
import com.flamingo.ai.notebooklm.service.memory.MemoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for memory management. */
@RestController
@RequestMapping("/api/sessions/{sessionId}/memories")
@RequiredArgsConstructor
@Slf4j
public class MemoryController {

  private final MemoryService memoryService;

  /**
   * Lists all memories for a session.
   *
   * @param sessionId the session ID
   * @return list of memories ordered by importance
   */
  @GetMapping
  public ResponseEntity<List<MemoryResponse>> listMemories(@PathVariable UUID sessionId) {
    List<Memory> memories = memoryService.getAllMemories(sessionId);
    List<MemoryResponse> response = memories.stream().map(MemoryResponse::fromEntity).toList();
    return ResponseEntity.ok(response);
  }

  /**
   * Gets a specific memory.
   *
   * @param sessionId the session ID
   * @param memoryId the memory ID
   * @return the memory
   */
  @GetMapping("/{memoryId}")
  public ResponseEntity<MemoryResponse> getMemory(
      @PathVariable UUID sessionId, @PathVariable UUID memoryId) {
    memoryService.validateMemoryOwnership(memoryId, sessionId);
    Memory memory = memoryService.getMemory(memoryId);
    return ResponseEntity.ok(MemoryResponse.fromEntity(memory));
  }

  /**
   * Manually creates a memory.
   *
   * @param sessionId the session ID
   * @param request the memory creation request
   * @return the created memory
   */
  @PostMapping
  public ResponseEntity<MemoryResponse> createMemory(
      @PathVariable UUID sessionId, @Valid @RequestBody CreateMemoryRequest request) {

    log.info("Creating manual memory for session {}: type={}", sessionId, request.getType());

    Memory memory =
        memoryService.addMemory(
            sessionId, request.getContent(), request.getType(), request.getImportance());

    return ResponseEntity.status(HttpStatus.CREATED).body(MemoryResponse.fromEntity(memory));
  }

  /**
   * Deletes a memory.
   *
   * @param sessionId the session ID
   * @param memoryId the memory ID
   * @return 204 No Content on success
   */
  @DeleteMapping("/{memoryId}")
  public ResponseEntity<Void> deleteMemory(
      @PathVariable UUID sessionId, @PathVariable UUID memoryId) {
    memoryService.validateMemoryOwnership(memoryId, sessionId);
    memoryService.deleteMemory(memoryId);
    return ResponseEntity.noContent().build();
  }
}
