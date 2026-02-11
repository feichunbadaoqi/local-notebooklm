package com.flamingo.ai.notebooklm.domain.repository;

import com.flamingo.ai.notebooklm.domain.entity.Memory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for Memory entities. */
@Repository
public interface MemoryRepository extends JpaRepository<Memory, UUID> {

  /** Finds all memories for a session ordered by importance (highest first). */
  List<Memory> findBySessionIdOrderByImportanceDesc(UUID sessionId);

  /** Finds memories by session and type. */
  List<Memory> findBySessionIdAndMemoryType(UUID sessionId, String memoryType);

  /** Finds top memories by importance for a session. */
  @Query("SELECT m FROM Memory m WHERE m.session.id = :sessionId " + "ORDER BY m.importance DESC")
  List<Memory> findTopMemoriesBySessionId(@Param("sessionId") UUID sessionId, Pageable pageable);

  /** Finds memories containing specific content (case-insensitive). */
  @Query(
      "SELECT m FROM Memory m WHERE m.session.id = :sessionId "
          + "AND LOWER(m.memoryContent) LIKE LOWER(CONCAT('%', :content, '%'))")
  List<Memory> findBySessionIdAndContentContaining(
      @Param("sessionId") UUID sessionId, @Param("content") String content);

  /** Counts memories by session. */
  long countBySessionId(UUID sessionId);

  /** Deletes all memories for a session. */
  void deleteBySessionId(UUID sessionId);
}
