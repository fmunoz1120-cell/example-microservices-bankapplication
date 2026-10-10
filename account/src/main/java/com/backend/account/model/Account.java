package com.backend.account.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account extends AuditedEntity {
  public Long getId() {
    return id;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "account_number", nullable = false, unique = true, length = 20)
  public String number;

  @Column(name = "account_type_id", nullable = false)
  public Long accountTypeId;

  @Column(name = "account_created_at", nullable = false, updatable = false)
  public Instant accountCreatedAt;

  @Column(name = "work_area_id", nullable = false)
  public Long workAreaId;

  @Column(name = "client_projection_id", nullable = false)
  public Long clientId;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal initialAmount;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal balance;

  @Column(nullable = false)
  public boolean isActive;

  @Column(name = "deleted_at")
  public Instant deletedAt;

  public void updateActiveState(boolean active) {
    isActive = active;
    if (active) {
      deletedAt = null;
    } else if (deletedAt == null) {
      deletedAt = Instant.now();
    }
  }
}
