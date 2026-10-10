package com.backend.account.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent extends AuditedEntity {
  @Id public UUID id;

  public ProcessedEvent() {}

  public ProcessedEvent(UUID id) {
    this.id = id;
  }
}
