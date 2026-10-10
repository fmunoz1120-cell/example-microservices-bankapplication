package com.backend.account.model;

import jakarta.persistence.*;

@Entity
@Table(name = "client_projections")
public class ClientProjection extends AuditedEntity {
  @Id public Long id;

  @Column(nullable = false)
  public String name;

  @Column(name = "is_active", nullable = false)
  public boolean active;

  @Column(nullable = false)
  public long eventVersion;
}
