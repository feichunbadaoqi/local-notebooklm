package com.flamingo.ai.notebooklm.domain.repository;

import com.flamingo.ai.notebooklm.domain.entity.ChatSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for ChatSummary entities. */
@Repository
public interface ChatSummaryRepository extends JpaRepository<ChatSummary, UUID> {

  /** Finds all summaries for a session ordered by timestamp. */
  List<ChatSummary> findBySessionIdOrderByFromTimestampAsc(UUID sessionId);

  /** Finds summaries for a session ordered by most recent. */
  List<ChatSummary> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

  /** Counts summaries by session. */
  long countBySessionId(UUID sessionId);

  /** Sums token count for all summaries in a session. */
  @Query(
      "SELECT COALESCE(SUM(s.tokenCount), 0) FROM ChatSummary s "
          + "WHERE s.session.id = :sessionId")
  int sumTokenCountBySessionId(@Param("sessionId") UUID sessionId);

  /** Calculates total tokens saved by compaction in a session. */
  @Query(
      "SELECT COALESCE(SUM(s.originalTokenCount - s.tokenCount), 0) FROM ChatSummary s "
          + "WHERE s.session.id = :sessionId")
  int sumTokensSavedBySessionId(@Param("sessionId") UUID sessionId);

  /** Deletes all summaries for a session. */
  void deleteBySessionId(UUID sessionId);
}
