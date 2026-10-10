package com.backend.client.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends AuditedEntity {
  @Id public UUID id;

  @Column(nullable = false)
  public Long clientId;

  @Column(nullable = false, columnDefinition = "text")
  public String payload;

  @Column(name = "is_published", nullable = false)
  public boolean published;
}
