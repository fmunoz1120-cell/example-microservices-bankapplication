package com.backend.client.repository;

import com.backend.client.model.OutboxEvent;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  @Query(
      value =
          "SELECT id, client_id, payload, is_published, created_at, updated_at FROM outbox_events"
              + " WHERE is_published = FALSE ORDER BY client_id, id LIMIT 100 FOR UPDATE SKIP"
              + " LOCKED",
      nativeQuery = true)
  List<OutboxEvent> pending();
}
