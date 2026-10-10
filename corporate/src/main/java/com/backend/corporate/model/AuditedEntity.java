package com.backend.corporate.model;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class AuditedEntity {
  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  public Instant updatedAt;
}
