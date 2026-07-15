package com.dbdeployer.store;

import com.dbdeployer.model.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

  List<ChatMessage> findBySessionIdOrderBySeqAsc(String sessionId);

  /** Messages newer than a seq — the turns not yet folded into the rolling summary. */
  List<ChatMessage> findBySessionIdAndSeqGreaterThanOrderBySeqAsc(String sessionId, int seq);

  int countBySessionId(String sessionId);

  /** Bulk-delete a session's messages (must run before the session row for the FK). */
  void deleteBySessionId(String sessionId);
}
