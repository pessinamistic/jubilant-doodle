package com.dbdeployer.store;

import com.dbdeployer.model.ChatSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

  /** All sessions, most recently active first (updated_at is null until the second turn). */
  @Query("select s from ChatSession s order by coalesce(s.updatedAt, s.createdAt) desc")
  List<ChatSession> findAllByRecency();
}
