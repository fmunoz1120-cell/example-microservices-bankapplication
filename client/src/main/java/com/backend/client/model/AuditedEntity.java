package com.backend.client.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@MappedSuperclass
public abstract class AuditedEntity {
  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  public Instant createdAt;

  @Generated(event = {EventType.INSERT, EventType.UPDATE})
  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  public Instant updatedAt;
}
