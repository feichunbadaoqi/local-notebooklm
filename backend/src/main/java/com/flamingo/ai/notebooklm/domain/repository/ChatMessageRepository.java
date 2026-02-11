package com.flamingo.ai.notebooklm.domain.repository;

import com.flamingo.ai.notebooklm.domain.entity.ChatMessage;
import com.flamingo.ai.notebooklm.domain.enums.MessageRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for ChatMessage entities. */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

  /** Finds all messages for a session ordered by creation time ascending. */
  List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

  /** Finds all messages for a session ordered by creation time descending. */
  List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

  /** Finds recent messages for a session (for sliding window). */
  @Query(
      "SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId " + "ORDER BY m.createdAt DESC")
  List<ChatMessage> findRecentMessages(@Param("sessionId") UUID sessionId, Pageable pageable);

  /** Finds uncompacted messages for a session. */
  @Query(
      "SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId "
          + "AND m.isCompacted = false ORDER BY m.createdAt ASC")
  List<ChatMessage> findUncompactedBySessionId(@Param("sessionId") UUID sessionId);

  /** Alias for findUncompactedBySessionId. */
  @Query(
      "SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId "
          + "AND m.isCompacted = false ORDER BY m.createdAt ASC")
  List<ChatMessage> findNonCompactedMessagesBySessionId(@Param("sessionId") UUID sessionId);

  /** Finds recent non-compacted messages up to a limit. */
  @Query(
      "SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId "
          + "AND m.isCompacted = false ORDER BY m.createdAt DESC")
  List<ChatMessage> findRecentNonCompactedMessagesQuery(
      @Param("sessionId") UUID sessionId, Pageable pageable);

  /** Finds recent non-compacted messages with a limit. */
  default List<ChatMessage> findRecentNonCompactedMessages(UUID sessionId, int limit) {
    return findRecentNonCompactedMessagesQuery(sessionId, Pageable.ofSize(limit));
  }

  /** Counts uncompacted messages for a session. */
  @Query(
      "SELECT COUNT(m) FROM ChatMessage m WHERE m.session.id = :sessionId "
          + "AND m.isCompacted = false")
  long countUncompactedBySessionId(@Param("sessionId") UUID sessionId);

  /** Counts messages created before a given timestamp. */
  @Query(
      "SELECT COUNT(m) FROM ChatMessage m WHERE m.session.id = :sessionId "
          + "AND m.createdAt < :timestamp")
  int countMessagesBeforeTimestamp(
      @Param("sessionId") UUID sessionId, @Param("timestamp") LocalDateTime timestamp);

  /** Sums token count for uncompacted messages in a session. */
  @Query(
      "SELECT COALESCE(SUM(m.tokenCount), 0) FROM ChatMessage m "
          + "WHERE m.session.id = :sessionId AND m.isCompacted = false")
  int sumTokenCountBySessionId(@Param("sessionId") UUID sessionId);

  /** Finds messages by session and role. */
  List<ChatMessage> findBySessionIdAndRole(UUID sessionId, MessageRole role);

  /** Counts messages by session. */
  long countBySessionId(UUID sessionId);

  /** Deletes all messages for a session. */
  void deleteBySessionId(UUID sessionId);
}
