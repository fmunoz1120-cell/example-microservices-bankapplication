package com.backend.account.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction extends AuditedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id")
  public Account account;

  @Column(name = "transaction_date", nullable = false)
  public Instant date;

  @Column(name = "transaction_type_id", nullable = false)
  public Long transactionTypeId;

  @Column(name = "work_area_id", nullable = false)
  public Long workAreaId;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal amount;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal balance;

  @Column(name = "reversed_transaction_id", unique = true)
  public Long reversalOf;

  @Column(name = "replaced_transaction_id")
  public Long replacementOf;

  @Column(name = "is_reversed", nullable = false)
  public boolean reversed;
}
