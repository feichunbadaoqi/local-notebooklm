package com.flamingo.ai.notebooklm.domain.repository;

import com.flamingo.ai.notebooklm.domain.entity.Session;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for Session entities. */
@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

  /** Finds all sessions ordered by last accessed time (most recent first). */
  List<Session> findAllByOrderByLastAccessedAtDesc();

  /** Finds sessions accessed after a given timestamp. */
  List<Session> findByLastAccessedAtAfterOrderByLastAccessedAtDesc(LocalDateTime after);

  /** Finds sessions by title containing the given text (case-insensitive). */
  @Query("SELECT s FROM Session s WHERE LOWER(s.title) LIKE LOWER(CONCAT('%', :title, '%'))")
  List<Session> findByTitleContainingIgnoreCase(@Param("title") String title);

  /** Counts the total number of messages across all sessions. */
  @Query("SELECT COUNT(m) FROM ChatMessage m")
  long countTotalMessages();
}
